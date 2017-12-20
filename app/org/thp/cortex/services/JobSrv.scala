package org.thp.cortex.services

import java.io.{ BufferedReader, InputStream, InputStreamReader }
import java.nio.file.{ Files, Path }
import java.util.Date
import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.{ BasicIO, Process, ProcessIO }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

import play.api.Logger
import play.api.libs.json.{ JsObject, JsString, Json }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import org.scalactic.Accumulation._
import org.scalactic.{ Bad, Good, One, Or }
import org.thp.cortex.models._

import org.elastic4play.controllers.{ AttachmentInputValue, Fields, FileInputValue }
import org.elastic4play.services._
import org.elastic4play._

@Singleton
class JobSrv @Inject() (
    jobModel: JobModel,
    reportModel: ReportModel,
    analyzerSrv: AnalyzerSrv,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    attachmentSrv: AttachmentSrv,
    akkaSystem: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  private lazy val logger = Logger(getClass)
  private lazy val analyzeExecutionContext: ExecutionContext =
    akkaSystem.dispatchers.lookup("analyzer")
  private val osexec =
    if (System.getProperty("os.name").toLowerCase.contains("win"))
      (c: Path) ⇒ s"""cmd /c $c"""
    else
      (c: Path) ⇒ s"""sh -c "./$c" """

  def list(dataTypeFilter: Option[String], dataFilter: Option[String], analyzerFilter: Option[String], range: Option[String]): (Source[Job, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    val filter = dataTypeFilter.map("dataType" like _).toList :::
      dataFilter.map("data" like _).toList :::
      analyzerFilter.map(af ⇒ or("analyzerId" like af, "analyzerName" like af)).toList
    find(and(filter: _*), range, Nil)
    //
    //
    //
    //    (jobActor ? ListJobs(dataTypeFilter, dataFilter, analyzerFilter, start, limit)).map {
    //      case JobList(total, jobs) ⇒ total → jobs
    //      case m                    ⇒ throw UnexpectedError(s"JobActor.list replies with unexpected message: $m (${m.getClass})")
    //    }
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    findSrv[JobModel, Job](jobModel, queryDef, range, sortBy)
  }

  def get(jobId: String): Future[Job] = getSrv[JobModel, Job](jobModel, jobId)

  def create(analyzerId: String, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    analyzerSrv.get(analyzerId).flatMap { analyzer ⇒
      val dataType = Or.from(fields.getString("dataType"), One(MissingAttributeError("dataType")))
      val dataFiv = (fields.getString("data"), fields.get("attachment")) match {
        case (Some(data), None)                ⇒ Good(Left(data))
        case (None, Some(fiv: FileInputValue)) ⇒ Good(Right(fiv))
        case (None, Some(other))               ⇒ Bad(One(InvalidFormatAttributeError("attachment", "attachment", other)))
        case (_, Some(fiv))                    ⇒ Bad(One(InvalidFormatAttributeError("data/attachment", "string/attachment", fiv)))
        case (None, None)                      ⇒ Bad(One(MissingAttributeError("data/attachment")))
      }

      val tlp = fields.getLong("tlp").getOrElse(2L)
      val message = fields.getString("message").getOrElse("")
      val parameters = fields.getValue("parameters").collect {
        case obj: JsObject ⇒ obj
      }
        .getOrElse(JsObject.empty)

      withGood(dataType, dataFiv) {
        case (dt, Right(fiv)) ⇒ dt -> attachmentSrv.save(fiv).map(Right.apply)
        case (dt, Left(data)) ⇒ dt -> Future.successful(Left(data))
      }
        .fold(
          typeDataAttachment ⇒ typeDataAttachment._2.flatMap(da ⇒ create(analyzer, typeDataAttachment._1, da, tlp, message, parameters)),
          errors ⇒ Future.failed(AttributeCheckingError("job", errors)))
    }
  }

  def create(analyzer: Analyzer, dataType: String, dataAttachment: Either[String, Attachment], tlp: Long, message: String, parameters: JsObject)(implicit authContext: AuthContext): Future[Job] = {
    val fields = Fields(Json.obj(
      "analyzerDefinitionId" -> analyzer.analyzerDefinitionId(),
      "analyzerId" -> analyzer.id,
      "analyzerName" -> analyzer.name(),
      "status" -> JobStatus.Waiting,
      "dataType" -> dataType,
      "tlp" -> tlp,
      "message" -> message,
      "parameters" -> parameters.toString))
    val fieldWithData = dataAttachment match {
      case Left(data)        ⇒ fields.set("data", data)
      case Right(attachment) ⇒ fields.set("attachment", AttachmentInputValue(attachment))
    }
    analyzerSrv.getDefinition(analyzer.analyzerDefinitionId()).flatMap { analyzerDefinition ⇒
      createSrv[JobModel, Job](jobModel, fieldWithData).andThen {
        case Success(job) ⇒ run(analyzerDefinition, analyzer, job)
      }
    }
  }

  def run(analyzerDefinition: AnalyzerDefinition, analyzer: Analyzer, job: Job)(implicit authContext: AuthContext): Future[Job] = {
    buildInput(analyzerDefinition, analyzer, job)
      .andThen { case _: Success[_] ⇒ startJob(job) }
      .flatMap { input ⇒
        val output = new StringBuffer
        val error = new StringBuffer
        try {
          logger.info(s"Execute ${osexec(analyzerDefinition.cmd)} in ${analyzerDefinition.baseDirectory}")
          Process(osexec(analyzerDefinition.cmd), analyzerDefinition.baseDirectory.toFile).run(
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

  private def buildInput(analyzerDefinition: AnalyzerDefinition, analyzer: Analyzer, job: Job) = {
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
        val configAndParam = analyzer.config.deepMerge(job.params)
        analyzerDefinition.configurationItems
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
    }(() ⇒ reader.readLine())
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