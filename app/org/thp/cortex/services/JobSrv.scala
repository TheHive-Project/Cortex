package org.thp.cortex.services

import java.io.{ BufferedReader, InputStream, InputStreamReader }
import java.nio.file.{ Files, Path }
import java.util.Date
import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.{ BasicIO, Process, ProcessIO }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

import play.api.{ Configuration, Logger }
import play.api.libs.json.{ JsObject, JsString, Json }

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink }
import org.thp.cortex.models._
import org.scalactic.Accumulation._

import org.elastic4play.{ AttributeCheckingError, NotFoundError }
import org.elastic4play.controllers.Fields
import org.elastic4play.services._

@Singleton
class JobSrv(
    analyzerPath: String,
    jobModel: JobModel,
    reportModel: ReportModel,
    analyzerSrv: AnalyzerSrv,
    analyzerConfigSrv: AnalyzerConfigSrv,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    attachmentSrv: AttachmentSrv,
    akkaSystem: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  @Inject() def this(
      config: Configuration,
      jobModel: JobModel,
      reportModel: ReportModel,
      analyzerSrv: AnalyzerSrv,
      analyzerConfigSrv: AnalyzerConfigSrv,
      getSrv: GetSrv,
      createSrv: CreateSrv,
      updateSrv: UpdateSrv,
      findSrv: FindSrv,
      attachmentSrv: AttachmentSrv,
      akkaSystem: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer) = this(
    config.get[String]("analyzers.path"),
    jobModel,
    reportModel,
    analyzerSrv,
    analyzerConfigSrv,
    getSrv,
    createSrv,
    updateSrv,
    findSrv,
    attachmentSrv,
    akkaSystem,
    ec,
    mat)

  private lazy val logger = Logger(getClass)
  private lazy val analyzeExecutionContext: ExecutionContext =
    akkaSystem.dispatchers.lookup("analyzer")
  private val osexec =
    if (System.getProperty("os.name").toLowerCase.contains("win"))
      (c: Path) ⇒ s"""cmd /c $c"""
    else
      (c: Path) ⇒ s"""sh -c "./$c" """

  //  def list(dataTypeFilter: Option[String], dataFilter: Option[String], analyzerFilter: Option[String], start: Int, limit: Int): Future[(Int, Seq[Job])] = {
  //    (jobActor ? ListJobs(dataTypeFilter, dataFilter, analyzerFilter, start, limit)).map {
  //      case JobList(total, jobs) ⇒ total → jobs
  //      case m                    ⇒ throw UnexpectedError(s"JobActor.list replies with unexpected message: $m (${m.getClass})")
  //    }
  //  }
  //
  def get(jobId: String): Future[Job] = getSrv[JobModel, Job](jobModel, jobId)

  def create(analyzerId: String, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    analyzerSrv.get(analyzerId).flatMap { analyzer ⇒
      create(analyzer, fields)
    }
  }

  def create(analyzer: Analyzer, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    createSrv[JobModel, Job](jobModel, fields).andThen {
      case Success(job) ⇒
        analyzerConfigSrv
          .get(authContext.userId, job.analyzerId())
          .map(analyzerConfig ⇒ run(analyzer, analyzerConfig, job))
    }
  }

  def run(analyzer: Analyzer, analyzerConfig: AnalyzerConfig, job: Job)(implicit authContext: AuthContext): Future[Job] = {
    buildInput(analyzer, analyzerConfig, job)
      .andThen { case _: Success[_] ⇒ startJob(job) }
      .flatMap { input ⇒
        val output = new StringBuffer
        val error = new StringBuffer
        try {
          logger.info(s"Execute ${osexec(analyzer.cmd)} in ${analyzer.cmd.getParent}")
          Process(osexec(analyzer.cmd), analyzer.cmd.getParent.toFile).run(
            new ProcessIO(
              { stdin ⇒ Try(stdin.write(input.toString.getBytes("UTF-8"))); stdin.close() },
              { stdout ⇒ readStream(output, stdout) },
              { stderr ⇒ readStream(error, stderr) }))
          val report = Json.parse(output.toString).as[JsObject]
          val success = (report \ "success").asOpt[Boolean].getOrElse(false)
          if (success) {
            val fullReport = (report \ "full").as[JsObject].toString
            val summaryReport = (report \ "summary").as[JsObject].toString
            val reportFields = Fields.empty
              .set("full", fullReport)
              .set("summary", summaryReport)
            createSrv[ReportModel, Report, Job](reportModel, job, reportFields)
              .transformWith {
                case Failure(e) ⇒ endJob(job, JobStatus.Failure, Some(s"Report creation failure: $e"))
                case _          ⇒ endJob(job, JobStatus.Success)
              }
          }
          else {
            endJob(job, JobStatus.Failure, Some((report \ "errorMessage").as[String]))
          }
        }
        catch {
          case NonFatal(_) ⇒
            val errorMessage = error.append(output).toString.take(8192)
            endJob(job, JobStatus.Failure, Some(s"Invalid output\n$errorMessage"))
        }
      }(analyzeExecutionContext)
  }

  def getReport(jobId: String): Future[Report] = get(jobId).flatMap(getReport)

  def getReport(job: Job): Future[Report] = {
    import QueryDSL._
    findSrv[ReportModel, Report](reportModel, withParent(job), Some("0-1"), Nil)._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Job ${job.id} has no report")))
  }

  private def buildInput(analyzer: Analyzer, analyzerConfig: AnalyzerConfig, job: Job) = {
    job.attachment()
      .map { attachment ⇒
        val tempFile = Files.createTempDirectory(s"cortex-job-${job.id}-")
        attachmentSrv.source(attachment.id).runWith(FileIO.toPath(tempFile))
          .flatMap {
            case ioresult if ioresult.status.isSuccess ⇒ Future.successful(Some(tempFile))
            case ioresult                              ⇒ Future.failed(ioresult.getError)
          }
      }
      .getOrElse(Future.successful(None))
      .map {
        case Some(file) ⇒
          Json.obj(
            "file" -> file.toString,
            "filename" -> job.attachment().get.name,
            "contentType" -> job.attachment().get.contentType)
        case None if job.data().nonEmpty ⇒
          Json.obj(
            "data" -> job.data().get)
      }
      .map { artifact ⇒
        val configAndParam = analyzerConfig.config.deepMerge(job.params)
        analyzer.configurationDefinition
          .validatedBy(_.read(configAndParam))
          .map(JsObject)
          .map(_ deepMerge artifact +
            ("dataType" -> JsString(job.dataType())) +
            ("message" -> JsString(job.message().getOrElse(""))))
          .badMap(e ⇒ AttributeCheckingError("", e.toSeq)) // FIXME model name
          .toTry
      }
      .flatMap(Future.fromTry)
  }

  private def startJob(job: Job)(implicit authContext: AuthContext): Future[Job] = {
    val fields = Fields.empty
      .set("status", JobStatus.InProgress.toString)
      .set("startDate", Json.toJson(new Date))
    updateSrv(job, fields)
  }

  private def endJob(job: Job, status: JobStatus.Type, errorMessage: Option[String] = None)(implicit authContext: AuthContext): Future[Job] = {
    val fields = Fields.empty
      .set("status", status.toString)
      .set("endDate", Json.toJson(new Date))
    updateSrv(job, errorMessage.fold(fields)(fields.set("errorMessage", _)))
  }

  private def readStream(buffer: StringBuffer, stream: InputStream): Unit = {
    val reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))
    try BasicIO.processLinesFully { line ⇒
      buffer.append(line).append(System.lineSeparator())
      ()
    }(reader.readLine _)
    finally reader.close()
  }

  //
  //  def waitReport(jobId: String, atMost: Duration): Future[Job] = {
  //    get(jobId).flatMap { job ⇒
  //      val finishedJob = job.report.map(_ ⇒ job)
  //      atMost match {
  //        case _: Infinite ⇒ finishedJob
  //        case duration: FiniteDuration ⇒
  //          val prom = Promise[Job]()
  //          val timeout = system.scheduler.scheduleOnce(duration) {
  //            prom.success(job)
  //            ()
  //          }
  //          finishedJob.onComplete(_ ⇒ timeout.cancel())
  //          Future.firstCompletedOf(List(finishedJob, prom.future))
  //      }
  //    }
  //  }
}

//
//object JobActor {
//  case class ListJobs(dataTypeFilter: Option[String], dataFilter: Option[String], analyzerFilter: Option[String], start: Int, limit: Int)
//  case class JobList(total: Int, jobs: Seq[Job])
//  case class GetJob(jobId: String)
//  case object JobNotFound
//  case class CreateJob(artifact: Artifact, analyzer: Analyzer, report: Future[Report])
//  case class RemoveJob(jobId: String)
//  case object JobRemoved
//  case object JobCleanup
//}
//
//class JobActor(
//    jobLifeTime: Duration,
//    jobCleanupPeriod: Duration,
//    analyzerSrv: AnalyzerSrv,
//    implicit val ec: ExecutionContext) extends Actor {
//
//  import services.JobActor._
//
//  @Inject def this(
//    configuration: Configuration,
//    analyzerSrv: AnalyzerSrv,
//    ec: ExecutionContext) =
//    this(
//      configuration.getString("job.lifetime").fold[Duration](Duration.Inf)(d ⇒ Duration(d)),
//      configuration.getString("job.cleanupPeriod").fold[Duration](Duration.Inf)(d ⇒ Duration(d)),
//      analyzerSrv,
//      ec)
//
//  lazy val logger = Logger(getClass)
//
//  (jobLifeTime, jobCleanupPeriod) match {
//    case (_: FiniteDuration, jcp: FiniteDuration) ⇒ context.system.scheduler.schedule(jcp, jcp, self, JobCleanup)
//    case (_: Infinite, _: Infinite)               ⇒ // no cleanup
//    case (_: FiniteDuration, _: Infinite)         ⇒ logger.warn("Job lifetime is configured but cleanup period is not set. Job will never be removed")
//    case (_: Infinite, _: FiniteDuration)         ⇒ logger.warn("Job cleanup period is configured but job lifetime is not set. Job will never be removed")
//  }
//
//  private[services] def removeJob(jobs: List[Job], jobId: String): Option[List[Job]] =
//    jobs.headOption match {
//      case Some(j) if j.id == jobId ⇒ Some(jobs.tail)
//      case Some(j)                  ⇒ removeJob(jobs.tail, jobId).map(j :: _)
//      case None                     ⇒ None
//    }
//
//  def jobState(jobs: List[Job]): Receive = {
//    case ListJobs(dataTypeFilter, dataFilter, analyzerFilter, start, limit) ⇒
//      val filteredJobs = jobs.filter(j ⇒
//        dataTypeFilter.fold(true)(j.artifact.dataTypeFilter) &&
//          dataFilter.fold(true)(j.artifact.dataFilter) &&
//          analyzerFilter.fold(true)(j.analyzer.id.contains))
//      sender ! JobList(filteredJobs.size, filteredJobs.slice(start, start + limit))
//    case GetJob(jobId) ⇒ sender ! jobs.find(_.id == jobId).getOrElse(JobNotFound)
//    case RemoveJob(jobId) ⇒
//      removeJob(jobs, jobId) match {
//        case Some(j) ⇒
//          sender ! JobRemoved
//          context.become(jobState(j))
//        case None ⇒ sender ! JobNotFound
//      }
//    case CreateJob(artifact, analyzer, report) ⇒
//      val jobId = Random.alphanumeric.take(16).mkString
//      val job = Job(jobId, analyzer, artifact, report)
//      sender ! job
//      context.become(jobState(job :: jobs))
//    case JobCleanup if jobLifeTime.isInstanceOf[FiniteDuration] ⇒
//      val now = (new Date).getTime
//      val limitDate = new Date(now - jobLifeTime.toMillis)
//      context.become(jobState(jobs.takeWhile(_.date after limitDate)))
//  }
//
//  override def receive: Receive = jobState(Nil)
//}