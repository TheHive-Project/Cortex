package org.thp.cortex.services

import java.io.{ ByteArrayOutputStream, InputStream }
import java.nio.file.{ Files, Paths }
import java.util.Date

import javax.inject.{ Inject, Singleton }
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }

import org.elastic4play._
import org.elastic4play.controllers._
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._
import org.scalactic.Accumulation._
import org.scalactic.{ Bad, Good, One, Or }
import org.thp.cortex.models._
import play.api.libs.json._
import play.api.{ Configuration, Logger }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.{ Process, ProcessIO }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

@Singleton
class JobSrv(
    jobCache: Duration,
    jobModel: JobModel,
    reportModel: ReportModel,
    artifactModel: ArtifactModel,
    workerSrv: WorkerSrv,
    userSrv: UserSrv,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    deleteSrv: DeleteSrv,
    attachmentSrv: AttachmentSrv,
    akkaSystem: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  @Inject() def this(
      configuration: Configuration,
      jobModel: JobModel,
      reportModel: ReportModel,
      artifactModel: ArtifactModel,
      workerSrv: WorkerSrv,
      userSrv: UserSrv,
      getSrv: GetSrv,
      createSrv: CreateSrv,
      updateSrv: UpdateSrv,
      findSrv: FindSrv,
      deleteSrv: DeleteSrv,
      attachmentSrv: AttachmentSrv,
      akkaSystem: ActorSystem,
      ec: ExecutionContext,
      mat: Materializer) = this(
    configuration.getOptional[Duration]("cache.job").getOrElse(Duration.Zero),
    jobModel,
    reportModel,
    artifactModel,
    workerSrv,
    userSrv,
    getSrv,
    createSrv,
    updateSrv,
    findSrv,
    deleteSrv,
    attachmentSrv,
    akkaSystem,
    ec, mat)

  private lazy val logger = Logger(getClass)
  private lazy val analyzerExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("analyzer")
  private lazy val responderExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("responder")
  private val osexec =
    if (System.getProperty("os.name").toLowerCase.contains("win"))
      (c: String) ⇒ s"""cmd /c $c"""
    else
      (c: String) ⇒ c

  runPreviousJobs()

  private def runPreviousJobs(): Unit = {
    import org.elastic4play.services.QueryDSL._
    userSrv.inInitAuthContext { implicit authContext ⇒
      find("status" ~= JobStatus.Waiting, Some("all"), Nil)
        ._1
        .runForeach { job ⇒
          (for {
            worker ← workerSrv.get(job.workerId())
            workerDefinition ← workerSrv.getDefinition(job.workerId())
            updatedJob ← run(workerDefinition, worker, job)
          } yield updatedJob)
            .onComplete {
              case Success(j) ⇒ logger.info(s"Job ${job.id} has finished with status ${j.status()}")
              case Failure(e) ⇒
                endJob(job, JobStatus.Failure, Some(e.getMessage), None)
                logger.error(s"Job ${job.id} has failed", e)
            }
        }
    }
  }

  private def withUserFilter[A](userId: String)(x: String ⇒ (Source[A, NotUsed], Future[Long])): (Source[A, NotUsed], Future[Long]) = {
    val a = userSrv.getOrganizationId(userId).map(x)
    val aSource = Source.fromFutureSource(a.map(_._1)).mapMaterializedValue(_ ⇒ NotUsed)
    val aTotal = a.flatMap(_._2)
    aSource → aTotal
  }

  def listForUser(userId: String, dataTypeFilter: Option[String], dataFilter: Option[String], analyzerFilter: Option[String], range: Option[String]): (Source[Job, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    findForUser(userId, and(
      dataTypeFilter.map("dataType" like _).toList :::
        dataFilter.map("data" like _).toList :::
        analyzerFilter.map(af ⇒ or("workerId" like af, "workerName" like af)).toList), range, Nil)
  }

  def findArtifacts(userId: String, jobId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Artifact, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    withUserFilter(userId) { organizationId ⇒
      findSrv[ArtifactModel, Artifact](artifactModel, and(queryDef, parent("report", parent("job", and(withId(jobId), "organization" ~= organizationId)))), range, sortBy)
    }
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    findSrv[JobModel, Job](jobModel, queryDef, range, sortBy)
  }

  def findForUser(userId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    withUserFilter(userId) { organizationId ⇒
      findForOrganization(organizationId, queryDef, range, sortBy)
    }
  }

  def findForOrganization(organizationId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    find(and("organization" ~= organizationId, queryDef), range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(jobModel, queryDef, aggs: _*)

  def getForUser(userId: String, jobId: String): Future[Job] = {
    import org.elastic4play.services.QueryDSL._
    withUserFilter(userId) { organizationId ⇒
      findForOrganization(organizationId, withId(jobId), Some("0-1"), Nil)
    }
      ._1
      .runWith(Sink.headOption)
      .flatMap {
        case Some(j) ⇒ Future.successful(j)
        case None    ⇒ Future.failed(NotFoundError(s"job $jobId not found"))
      }
  }

  def delete(job: Job)(implicit authContext: AuthContext): Future[Job] = deleteSrv(job)

  def legacyCreate(worker: Worker, attributes: JsObject, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    val dataType = Or.from((attributes \ "dataType").asOpt[String], One(MissingAttributeError("dataType")))
    val dataFiv = fields.get("data") match {
      case Some(fiv: FileInputValue)            ⇒ Good(Right(fiv))
      case Some(StringInputValue(Seq(data)))    ⇒ Good(Left(data))
      case Some(JsonInputValue(JsString(data))) ⇒ Good(Left(data))
      case Some(iv)                             ⇒ Bad(One(InvalidFormatAttributeError("data", "string/attachment", iv)))
      case None                                 ⇒ Bad(One(MissingAttributeError("data")))
    }
    val tlp = (attributes \ "tlp").asOpt[Long].getOrElse(2L)
    val pap = (attributes \ "pap").asOpt[Long].getOrElse(2L)
    val message = (attributes \ "message").asOpt[String].getOrElse("")
    val parameters = (attributes \ "parameters").asOpt[JsObject].getOrElse(JsObject.empty)
    val label = (attributes \ "label").asOpt[String]
    val force = fields.getBoolean("force").getOrElse(false)
    withGood(dataType, dataFiv) {
      case (dt, Right(fiv)) ⇒ dt → attachmentSrv.save(fiv).map(Right.apply)
      case (dt, Left(data)) ⇒ dt → Future.successful(Left(data))
    }
      .fold(
        typeDataAttachment ⇒ typeDataAttachment._2.flatMap(
          da ⇒ create(worker, typeDataAttachment._1, da, tlp, pap, message, parameters, label, force)),
        errors ⇒ {
          val attributeError = AttributeCheckingError("job", errors)
          logger.error("legacy job create fails", attributeError)
          Future.failed(attributeError)
        })
  }

  def create(workerId: String, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    workerSrv.getForUser(authContext.userId, workerId).flatMap { worker ⇒
      /*
      In Cortex 1, fields looks like:
      {
        "data": "127.0.0.1",
        "attributes": {
          "dataType": "ip",
          "tlp": 2
          "extra attributes": "value"
        }
      }
      - or -
      {
        "dataType": "file",
        "tlp": 2
        "extra attributes": "value"
        "attachment": {
          "name / id / content-type / ..."
        }
      }

      In Cortex 2, fields looks like:
      {
        "data": "127.0.0.1",
        "dataType": "ip",
        "tlp": 2,
        "message": "optional message",
        "parameters": {
          "optional parameters": "value"
        }
       */
      fields.getValue("attributes").map(attributes ⇒ legacyCreate(worker, attributes.as[JsObject], fields)).getOrElse {
        val dataType = Or.from(fields.getString("dataType"), One(MissingAttributeError("dataType")))
        val dataFiv = (fields.get("data"), fields.getString("data"), fields.get("attachment")) match {
          case (_, Some(data), None)                ⇒ Good(Left(data))
          case (_, None, Some(fiv: FileInputValue)) ⇒ Good(Right(fiv))
          case (Some(fiv: FileInputValue), None, _) ⇒ Good(Right(fiv))
          case (_, None, Some(other))               ⇒ Bad(One(InvalidFormatAttributeError("attachment", "attachment", other)))
          case (_, _, Some(fiv))                    ⇒ Bad(One(InvalidFormatAttributeError("data/attachment", "string/attachment", fiv)))
          case (_, None, None)                      ⇒ Bad(One(MissingAttributeError("data/attachment")))
        }

        val tlp = fields.getLong("tlp").getOrElse(2L)
        val pap = fields.getLong("pap").getOrElse(2L)
        val message = fields.getString("message").getOrElse("")
        val force = fields.getBoolean("force").getOrElse(false)
        val parameters = fields.getValue("parameters").collect {
          case obj: JsObject ⇒ obj
        }
          .getOrElse(JsObject.empty)

        withGood(dataType, dataFiv) {
          case (dt, Right(fiv)) ⇒ dt → attachmentSrv.save(fiv).map(Right.apply)
          case (dt, Left(data)) ⇒ dt → Future.successful(Left(data))
        }
          .fold(
            typeDataAttachment ⇒ typeDataAttachment._2.flatMap(da ⇒ create(worker, typeDataAttachment._1, da, tlp, pap, message, parameters, fields.getString("label"), force)),
            errors ⇒ Future.failed(AttributeCheckingError("job", errors)))
      }
    }
  }

  def create(
      worker: Worker,
      dataType: String,
      dataAttachment: Either[String, Attachment],
      tlp: Long,
      pap: Long,
      message: String,
      parameters: JsObject,
      label: Option[String],
      force: Boolean)(implicit authContext: AuthContext): Future[Job] = {
    val previousJob = if (force) Future.successful(None)
    else findSimilarJob(worker, dataType, dataAttachment, tlp, parameters)
    previousJob.flatMap {
      case Some(job) ⇒ Future.successful(job)
      case None ⇒ isUnderRateLimit(worker).flatMap {
        case true ⇒
          val fields = Fields(Json.obj(
            "workerDefinitionId" → worker.workerDefinitionId(),
            "workerId" → worker.id,
            "workerName" → worker.name(),
            "organization" → worker.parentId,
            "status" → JobStatus.Waiting,
            "dataType" → dataType,
            "tlp" → tlp,
            "pap" → pap,
            "message" → message,
            "parameters" → parameters.toString,
            "type" → worker.tpe()))
            .set("label", label.map(JsString.apply))
          val fieldWithData = dataAttachment match {
            case Left(data)        ⇒ fields.set("data", data)
            case Right(attachment) ⇒ fields.set("attachment", AttachmentInputValue(attachment))
          }
          workerSrv.getDefinition(worker.workerDefinitionId()).flatMap { workerDefinition ⇒
            createSrv[JobModel, Job](jobModel, fieldWithData).andThen {
              case Success(job) ⇒
                run(workerDefinition, worker, job)
                  .onComplete {
                    case Success(j) ⇒ logger.info(s"Job ${job.id} has finished with status ${j.status()}")
                    case Failure(e) ⇒ logger.error(s"Job ${job.id} has failed", e)
                  }
            }
          }
        case false ⇒
          Future.failed(RateLimitExceeded(worker))

      }
    }
  }

  private def isUnderRateLimit(worker: Worker): Future[Boolean] = {
    (for {
      rate ← worker.rate()
      rateUnit ← worker.rateUnit()
    } yield {
      import org.elastic4play.services.QueryDSL._
      val now = new Date().getTime
      logger.info(s"Checking rate limit on worker ${worker.name()} from ${new Date(now - rateUnit.id.toLong * 24 * 60 * 60 * 1000)}")
      stats(and("createdAt" ~>= (now - rateUnit.id.toLong * 24 * 60 * 60 * 1000), "workerId" ~= worker.id), Seq(selectCount)).map { s ⇒
        val count = (s \ "count").as[Long]
        logger.info(s"$count analysis found (limit is $rate)")
        count < rate
      }
    })
      .getOrElse(Future.successful(true))
  }

  def findSimilarJob(worker: Worker, dataType: String, dataAttachment: Either[String, Attachment], tlp: Long, parameters: JsObject): Future[Option[Job]] = {
    val cache = worker.jobCache().fold(jobCache)(_.minutes)
    if (cache.length == 0 || worker.tpe() == WorkerType.responder) {
      logger.info("Job cache is disabled")
      Future.successful(None)
    }
    else {
      import org.elastic4play.services.QueryDSL._
      logger.info(s"Looking for similar job (worker=${worker.id}, dataType=$dataType, data=$dataAttachment, tlp=$tlp, parameters=$parameters")
      val now = new Date().getTime
      find(and(
        "workerId" ~= worker.id,
        "status" ~!= JobStatus.Failure,
        "status" ~!= JobStatus.Deleted,
        "startDate" ~>= (now - cache.toMillis),
        "dataType" ~= dataType,
        "tlp" ~= tlp,
        dataAttachment.fold(data ⇒ "data" ~= data, attachment ⇒ "attachment.id" ~= attachment.id),
        "parameters" ~= parameters.toString), Some("0-1"), Seq("-createdAt"))
        ._1
        .map(j ⇒ new Job(jobModel, j.attributes + ("fromCache" → JsBoolean(true))))
        .runWith(Sink.headOption)
    }
  }

  private def fixArtifact(artifact: Fields): Fields = {
    def rename(oldName: String, newName: String): Fields ⇒ Fields = fields ⇒
      fields.getValue(oldName).fold(fields)(v ⇒ fields.unset(oldName).set(newName, v))

    rename("value", "data").andThen(
      rename("type", "dataType"))(artifact)
  }

  def run(workerDefinition: WorkerDefinition, worker: Worker, job: Job)(implicit authContext: AuthContext): Future[Job] = {
    val executionContext = workerDefinition.tpe match {
      case WorkerType.analyzer  ⇒ analyzerExecutionContext
      case WorkerType.responder ⇒ responderExecutionContext
    }
    buildInput(workerDefinition, worker, job)
      .flatMap { input ⇒
        startJob(job)
        var output = ""
        var error = ""
        try {
          logger.info(s"Execute ${osexec(workerDefinition.command)} in ${workerDefinition.baseDirectory}")
          Process(osexec(workerDefinition.command), workerDefinition.baseDirectory.toFile).run(
            new ProcessIO(
              { stdin ⇒ Try(stdin.write(input.toString.getBytes("UTF-8"))); stdin.close() },
              { stdout ⇒ output = readStream(stdout) },
              { stderr ⇒ error = readStream(stderr) }))
            .exitValue()
          val report = Json.parse(output).as[JsObject]
          val success = (report \ "success").asOpt[Boolean].getOrElse(false)
          if (success) {
            val fullReport = (report \ "full").as[JsObject].toString
            val summaryReport = (report \ "summary").asOpt[JsObject].getOrElse(JsObject.empty).toString
            val artifacts = (report \ "artifacts").asOpt[Seq[JsObject]].getOrElse(Nil)
            val operations = (report \ "operations").asOpt[Seq[JsObject]].getOrElse(Nil)
            val reportFields = Fields.empty
              .set("full", fullReport)
              .set("summary", summaryReport)
              .set("operations", JsArray(operations).toString)
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
            endJob(job, JobStatus.Failure,
              (report \ "errorMessage").asOpt[String],
              (report \ "input").asOpt[JsValue].map(_.toString))
          }
        }
        catch {
          case NonFatal(_) ⇒
            val errorMessage = (error + output).take(8192)
            endJob(job, JobStatus.Failure, Some(s"Invalid output\n$errorMessage"))
        }
        finally {
          (input \ "file").asOpt[String].foreach { filename ⇒
            Files.deleteIfExists(Paths.get(filename))
          }
        }
      }(executionContext)
  }

  def getReport(jobId: String)(implicit authContext: AuthContext): Future[Report] = getForUser(authContext.userId, jobId).flatMap(getReport)

  def getReport(job: Job): Future[Report] = {
    import org.elastic4play.services.QueryDSL._
    findSrv[ReportModel, Report](reportModel, withParent(job), Some("0-1"), Nil)._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Job ${job.id} has no report")))
  }

  private def buildInput(workerDefinition: WorkerDefinition, worker: Worker, job: Job): Future[JsObject] = {
    job.attachment()
      .map { attachment ⇒
        val tempFile = Files.createTempFile(s"cortex-job-${job.id}-", "")
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
            "file" → file.toString,
            "filename" → job.attachment().get.name,
            "contentType" → job.attachment().get.contentType)
        case None if job.data().nonEmpty && job.tpe() == WorkerType.responder ⇒
          Json.obj("data" → Json.parse(job.data().get))
        case None if job.data().nonEmpty && job.tpe() == WorkerType.analyzer ⇒
          Json.obj("data" → job.data().get)
      }
      .map { artifact ⇒
        (BaseConfig.global(worker.tpe()).items ++ BaseConfig.tlp.items ++ BaseConfig.pap.items ++ workerDefinition.configurationItems)
          .validatedBy(_.read(worker.config))
          .map(cfg ⇒ Json.obj("config" → JsObject(cfg).deepMerge(workerDefinition.configuration)))
          .map { cfg ⇒
            val proxy_http = (cfg \ "config" \ "proxy_http").asOpt[String].fold(JsObject.empty) { proxy ⇒ Json.obj("proxy" → Json.obj("http" → proxy)) }
            val proxy_https = (cfg \ "config" \ "proxy_https").asOpt[String].fold(JsObject.empty) { proxy ⇒ Json.obj("proxy" → Json.obj("https" → proxy)) }
            cfg.deepMerge(Json.obj("config" → proxy_http.deepMerge(proxy_https)))
          }
          .map(_ deepMerge artifact +
            ("dataType" → JsString(job.dataType())) +
            ("tlp" → JsNumber(job.tlp())) +
            ("pap" → JsNumber(job.pap())) +
            ("message" → JsString(job.message().getOrElse(""))) +
            ("parameters" → job.params))
          .badMap(e ⇒ AttributeCheckingError("job", e.toSeq))
          .toTry
      }
      .flatMap(Future.fromTry)
  }

  //
  private def startJob(job: Job)(implicit authContext: AuthContext): Future[Job] = {
    val fields = Fields.empty
      .set("status", JobStatus.InProgress.toString)
      .set("startDate", Json.toJson(new Date))
    updateSrv(job, fields, ModifyConfig(retryOnConflict = 0))
  }

  private def endJob(job: Job, status: JobStatus.Type, errorMessage: Option[String] = None, input: Option[String] = None)(implicit authContext: AuthContext): Future[Job] = {
    val fields = Fields.empty
      .set("status", status.toString)
      .set("endDate", Json.toJson(new Date))
      .set("input", input.map(JsString.apply))
      .set("message", errorMessage.map(JsString.apply))
    updateSrv(job, fields, ModifyConfig.default)
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