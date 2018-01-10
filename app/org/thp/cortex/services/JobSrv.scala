package org.thp.cortex.services

import java.io.{ ByteArrayOutputStream, InputStream }
import java.nio.file.{ Files, Path }
import java.util.Date
import javax.inject.{ Inject, Singleton }

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.{ Process, ProcessIO }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

import play.api.{ Configuration, Logger }
import play.api.libs.json.{ JsObject, JsString, Json }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import org.scalactic.Accumulation._
import org.scalactic.{ Bad, Good, One, Or }
import org.thp.cortex.models._

import org.elastic4play._
import org.elastic4play.controllers.{ AttachmentInputValue, Fields, FileInputValue }
import org.elastic4play.models.{ AbstractModelDef, BaseEntity }
import org.elastic4play.services._

@Singleton
class JobSrv(
    jobCache: FiniteDuration,
    jobModel: JobModel,
    reportModel: ReportModel,
    artifactModel: ArtifactModel,
    analyzerSrv: AnalyzerSrv,
    userSrv: UserSrv,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    attachmentSrv: AttachmentSrv,
    akkaSystem: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  @Inject() def this(
      configuration: Configuration,
      jobModel: JobModel,
      reportModel: ReportModel,
      artifactModel: ArtifactModel,
      analyzerSrv: AnalyzerSrv,
      userSrv: UserSrv,
      getSrv: GetSrv,
      createSrv: CreateSrv,
      updateSrv: UpdateSrv,
      findSrv: FindSrv,
      attachmentSrv: AttachmentSrv,
      akkaSystem: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer) = this(
    configuration.getOptional[FiniteDuration]("job.cache").getOrElse(Duration.Zero),
    jobModel,
    reportModel,
    artifactModel,
    analyzerSrv,
    userSrv,
    getSrv,
    createSrv,
    updateSrv,
    findSrv,
    attachmentSrv,
    akkaSystem,
    ec, mat)

  private lazy val logger = Logger(getClass)
  private lazy val analyzeExecutionContext: ExecutionContext =
    akkaSystem.dispatchers.lookup("analyzer")
  private val osexec =
    if (System.getProperty("os.name").toLowerCase.contains("win"))
      (c: Path) ⇒ s"""cmd /c $c"""
    else
      (c: Path) ⇒ s"""sh -c "$c" """

  private def findWithUserFilter[M <: AbstractModelDef[M, E], E <: BaseEntity](m: M, queryDef: QueryDef ⇒ QueryDef, range: Option[String], sortBy: Seq[String])(implicit authContext: AuthContext): (Source[E, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    if (authContext.roles.contains(Roles.admin)) {
      findSrv[M, E](m, queryDef(any), range, sortBy)
    }
    else {
      val futureSource = userSrv.getOrganizationId(authContext.userId).map { organizationId ⇒
        findSrv[M, E](m, queryDef("organizationId" ~= organizationId), range, sortBy)
      }
      val source = Source.fromFutureSource(futureSource.map(_._1)).mapMaterializedValue(_ ⇒ NotUsed)
      source -> futureSource.flatMap(_._2)
    }
  }

  def list(dataTypeFilter: Option[String], dataFilter: Option[String], analyzerFilter: Option[String], range: Option[String])(implicit authContext: AuthContext): (Source[Job, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    findWithUserFilter[JobModel, Job](jobModel, userFilter ⇒
      and(userFilter ::
        dataTypeFilter.map("dataType" like _).toList :::
        dataFilter.map("data" like _).toList :::
        analyzerFilter.map(af ⇒ or("analyzerId" like af, "analyzerName" like af)).toList), range, Nil)
  }

  def findArtifacts(jobId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String])(implicit authContext: AuthContext): (Source[Artifact, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    findWithUserFilter[ArtifactModel, Artifact](artifactModel, userFilter ⇒ and(queryDef, parent("report", parent("job", and(withId(jobId), userFilter)))), range, sortBy)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    findSrv[JobModel, Job](jobModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(jobModel, queryDef, aggs: _*)

  def get(jobId: String)(implicit authContext: AuthContext): Future[Job] = {
    import org.elastic4play.services.QueryDSL._
    findWithUserFilter[JobModel, Job](jobModel, userFilter ⇒ and(userFilter, withId(jobId)), Some("0-1"), Nil)
      ._1
      .runWith(Sink.head)
  }

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
    findSimilarJob(analyzer, dataType, dataAttachment, parameters).flatMap {
      case Some(job) ⇒ Future.successful(job)
      case None ⇒ isUnderRateLimit(analyzer).flatMap {
        case true ⇒
          val fields = Fields(Json.obj(
            "analyzerDefinitionId" -> analyzer.analyzerDefinitionId(),
            "analyzerId" -> analyzer.id,
            "analyzerName" -> analyzer.name(),
            "organizationId" -> analyzer.parentId,
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
              case Success(job) ⇒ run(analyzerDefinition, analyzer, job).onComplete(e ⇒ println(s"DEBUG: job run : $e"))
            }
          }
        case false ⇒
          Future.failed(RateLimitExceeded(analyzer))

      }
    }
  }

  private def isUnderRateLimit(analyzer: Analyzer): Future[Boolean] = {
    (for {
      rate ← analyzer.rate()
      rateUnit ← analyzer.rateUnit()
    } yield {
      import org.elastic4play.services.QueryDSL._
      val now = new Date().getTime * 1000
      stats(and("createdAt" ~>= (now - rateUnit.id), "analyzerId" ~= analyzer.id), Seq(selectCount)).map { stats ⇒
        (stats \ "count").as[Long] < rate
      }
    })
      .getOrElse(Future.successful(true))
  }

  def findSimilarJob(analyzer: Analyzer, dataType: String, dataAttachment: Either[String, Attachment], parameters: JsObject): Future[Option[Job]] = {
    if (jobCache.length == 0) {
      Future.successful(None)
    }
    else {
      import org.elastic4play.services.QueryDSL._
      val now = new Date().getTime * 1000
      find(and(
        "analyzerId" ~= analyzer.id,
        "status" ~!= JobStatus.Failure,
        "startDate" ~>= (now - jobCache.toSeconds),
        "dataType" ~= dataType,
        dataAttachment.fold(dataType ⇒ "dataType" ~= dataType, attachment ⇒ "attachment.id" ~= attachment.id),
        "parameters" ~= parameters.toString), Some("0-1"), Seq("-createdAt"))
        ._1
        .runWith(Sink.headOption)
    }
  }

  private def fixArtifact(artifact: Fields): Fields = {
    def rename(oldName: String, newName: String): Fields ⇒ Fields = fields ⇒
      fields.getValue(oldName).fold(fields)(v ⇒ fields.unset(oldName).set(newName, v))

    rename("value", "data").andThen(
      rename("type", "dataType"))(artifact)
  }

  def run(analyzerDefinition: AnalyzerDefinition, analyzer: Analyzer, job: Job)(implicit authContext: AuthContext): Future[Job] = {
    buildInput(analyzerDefinition, analyzer, job)
      .andThen { case _: Success[_] ⇒ startJob(job) }
      .flatMap { input ⇒
        var output = ""
        var error = ""
        try {
          logger.info(s"Execute ${osexec(analyzerDefinition.cmd)} in ${analyzerDefinition.baseDirectory}")
          Process(osexec(analyzerDefinition.cmd), analyzerDefinition.baseDirectory.toFile).run(
            new ProcessIO(
              { stdin ⇒ Try(stdin.write(input.toString.getBytes("UTF-8"))); stdin.close() },
              { stdout ⇒ output = readStream(stdout) },
              { stderr ⇒ error = readStream(stderr) }))
            .exitValue()
          val report = Json.parse(output).as[JsObject]
          val success = (report \ "success").asOpt[Boolean].getOrElse(false)
          if (success) {
            val fullReport = (report \ "full").as[JsObject].toString
            val summaryReport = (report \ "summary").as[JsObject].toString
            val artifacts = (report \ "artifacts").asOpt[Seq[JsObject]].getOrElse(Nil)
            val reportFields = Fields.empty
              .set("full", fullReport)
              .set("summary", summaryReport)
            createSrv[ReportModel, Report, Job](reportModel, job, reportFields)
              .flatMap { report ⇒
                Future.traverse(artifacts) { artifact ⇒
                  createSrv[ArtifactModel, Artifact, Report](artifactModel, report, fixArtifact(Fields(artifact)))
                }
              }
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
            val errorMessage = (error + output).take(8192)
            endJob(job, JobStatus.Failure, Some(s"Invalid output\n$errorMessage"))
        }
      }(analyzeExecutionContext)
  }

  def getReport(jobId: String)(implicit authContext: AuthContext): Future[Report] = get(jobId).flatMap(getReport)

  def getReport(job: Job): Future[Report] = {
    import QueryDSL._
    findSrv[ReportModel, Report](reportModel, withParent(job), Some("0-1"), Nil)._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Job ${job.id} has no report")))
  }

  private def buildInput(analyzerDefinition: AnalyzerDefinition, analyzer: Analyzer, job: Job): Future[JsObject] = {
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
          .map(cfg ⇒ Json.obj("config" -> JsObject(cfg)))
          .map(_ deepMerge artifact +
            ("dataType" -> JsString(job.dataType())) +
            ("message" -> JsString(job.message().getOrElse(""))))
          .badMap(e ⇒ AttributeCheckingError("job", e.toSeq))
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
    updateSrv(job, errorMessage.fold(fields)(fields.set("message", _)))
  }

  private def readStream(stream: InputStream): String = {
    val out = new ByteArrayOutputStream()
    val buffer = Array.ofDim[Byte](4096)
    Stream.continually(stream.read(buffer))
      .takeWhile(_ != -1)
      .foreach(out.write(buffer, 0, _))
    out.toString("UTF-8")
  }
}