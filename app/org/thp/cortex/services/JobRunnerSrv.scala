package org.thp.cortex.services

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Failure

import play.api.libs.json._
import play.api.{ Configuration, Logger }

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import javax.inject.Inject
import org.thp.cortex.models._

import org.elastic4play.BadRequestError
import org.elastic4play.controllers.{ Fields, FileInputValue }
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services.{ AttachmentSrv, AuthContext, CreateSrv, UpdateSrv }

class JobRunnerSrv @Inject() (
    config: Configuration,
    reportModel: ReportModel,
    artifactModel: ArtifactModel,
    processJobRunnerSrv: ProcessJobRunnerSrv,
    dockerJobRunnerSrv: DockerJobRunnerSrv,
    workerSrv: WorkerSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    attachmentSrv: AttachmentSrv,
    akkaSystem: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  val logger = Logger(getClass)
  lazy val analyzerExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("analyzer")
  lazy val responderExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("responder")

  private val runners: Seq[String] = config
    .getOptional[Seq[String]]("runners")
    .getOrElse(Seq("docker", "process"))
    .map(_.toLowerCase)

  private object deleteVisitor extends SimpleFileVisitor[Path] {
    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
      Files.delete(file)
      FileVisitResult.CONTINUE
    }

    override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
      Files.delete(dir)
      FileVisitResult.CONTINUE
    }
  }

  private def delete(directory: Path): Unit = try {
    if (Files.exists(directory))
      Files.walkFileTree(directory, deleteVisitor)
    ()
  }
  catch {
    case t: Throwable ⇒ logger.warn(s"Fail to remove temporary files ($directory) : $t")
  }

  private def prepareJobFolder(worker: Worker, job: Job): Future[Path] = {
    val jobFolder = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), s"cortex-job-${job.id}-")
    val inputJobFolder = Files.createDirectories(jobFolder.resolve("input"))
    Files.createDirectories(jobFolder.resolve("output"))

    job.attachment()
      .map { attachment ⇒
        val attachmentFile = Files.createTempFile(inputJobFolder, "attachment", "")
        attachmentSrv.source(attachment.id).runWith(FileIO.toPath(attachmentFile))
          .flatMap {
            case ioresult if ioresult.status.isSuccess ⇒ Future.successful(Some(attachmentFile))
            case ioresult                              ⇒ Future.failed(ioresult.getError)
          }
      }
      .getOrElse(Future.successful(None))
      .map {
        case Some(file) ⇒
          Json.obj(
            "file" → file.toString, // FIXME set only the temporary file name
            "filename" → job.attachment().get.name,
            "contentType" → job.attachment().get.contentType)
        case None if job.data().nonEmpty && job.tpe() == WorkerType.responder ⇒
          Json.obj("data" → Json.parse(job.data().get))
        case None if job.data().nonEmpty && job.tpe() == WorkerType.analyzer ⇒
          Json.obj("data" → job.data().get)
      }
      .map { artifact ⇒
        val proxy_http = (worker.config \ "proxy_http").asOpt[String].fold(JsObject.empty) { proxy ⇒ Json.obj("proxy" → Json.obj("http" → proxy)) }
        val proxy_https = (worker.config \ "proxy_https").asOpt[String].fold(JsObject.empty) { proxy ⇒ Json.obj("proxy" → Json.obj("https" → proxy)) }
        val config = worker.config.deepMerge(proxy_http).deepMerge(proxy_https)
        (worker.config \ "cacerts").asOpt[String].foreach { cacerts ⇒
          val cacertsFile = jobFolder.resolve("input").resolve("cacerts")
          Files.write(cacertsFile, cacerts.getBytes)
        }
        artifact +
          ("dataType" → JsString(job.dataType())) +
          ("tlp" → JsNumber(job.tlp())) +
          ("pap" → JsNumber(job.pap())) +
          ("message" → JsString(job.message().getOrElse(""))) +
          ("parameters" → job.params) +
          ("config" -> config)
      }
      .map { input ⇒
        Files.write(inputJobFolder.resolve("input.json"), input.toString.getBytes(StandardCharsets.UTF_8))
        jobFolder
      }
      .recoverWith {
        case error ⇒
          delete(jobFolder)
          Future.failed(error)
      }
  }

  private def extractReport(jobFolder: Path, job: Job)(implicit authContext: AuthContext) = {
    val outputFile = jobFolder.resolve("output").resolve("output.json")
    if (Files.exists(outputFile)) {
      val is = Files.newInputStream(outputFile)
      val report = Json.parse(is)
      is.close()

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
            Future.sequence {
              for {
                artifact ← artifacts
                dataType ← (artifact \ "dataType").asOpt[String]
                fields ← dataType match {
                  case "file" ⇒
                    for {
                      name ← (artifact \ "filename").asOpt[String]
                      file ← (artifact \ "file").asOpt[String]
                      path = jobFolder.resolve("output").resolve(file)
                      if Files.exists(path) && path.getParent == jobFolder.resolve("output")
                      contentType = (artifact \ "contentType").asOpt[String].getOrElse("application/octet-stream")
                      fiv = FileInputValue(name, path, contentType)
                    } yield Fields(artifact - "filename" - "file" - "contentType").set("attachment", fiv)
                  case _ ⇒ Some(Fields(artifact))
                }
              } yield createSrv[ArtifactModel, Artifact, Report](artifactModel, report, fields)
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
    else {
      endJob(job, JobStatus.Failure, Some(s"no output"))
    }
  }

  //  private def fixArtifact(artifact: Fields): Fields = {
  //    def rename(oldName: String, newName: String): Fields ⇒ Fields = fields ⇒
  //      fields.getValue(oldName).fold(fields)(v ⇒ fields.unset(oldName).set(newName, v))
  //
  //    rename("value", "data").andThen(
  //      rename("type", "dataType"))(artifact)
  //  }

  def run(worker: Worker, job: Job)(implicit authContext: AuthContext): Future[Job] = {
    prepareJobFolder(worker, job).flatMap { jobFolder ⇒
      val finishedJob = for {
        workerDefinition ← workerSrv.getDefinition(worker.workerDefinitionId())
        executionContext = workerDefinition.tpe match {
          case WorkerType.analyzer  ⇒ analyzerExecutionContext
          case WorkerType.responder ⇒ responderExecutionContext
        }
        _ ← startJob(job)
        j ← runners
          .foldLeft[Option[Future[Unit]]](None) {
            case (None, "docker") ⇒
              worker.dockerImage()
                .map(dockerImage ⇒ dockerJobRunnerSrv.run(jobFolder, dockerImage, job)(executionContext))
                .orElse {
                  logger.warn(s"worker ${worker.id} can't be run with docker (doesn't have image)")
                  None
                }
            case (None, "process") ⇒

              worker.command()
                .map(command ⇒ processJobRunnerSrv.run(jobFolder, command, job)(executionContext))
                .orElse {
                  logger.warn(s"worker ${worker.id} can't be run with process (doesn't have image)")
                  None
                }
            case (j: Some[_], _) ⇒ j
            case (None, runner) ⇒
              logger.warn(s"Unknown job runner: $runner")
              None

          }
          .getOrElse(Future.failed(BadRequestError("Worker cannot be run")))
      } yield j
      finishedJob
        .transformWith { r ⇒
          r.fold(
            error ⇒ endJob(job, JobStatus.Failure, Option(error.getMessage), Some(readFile(jobFolder.resolve("input").resolve("input.json")))),
            _ ⇒ extractReport(jobFolder, job))
        }
      //.andThen { case _ ⇒ delete(jobFolder) }
    }
  }

  private def readFile(input: Path): String = new String(Files.readAllBytes(input), StandardCharsets.UTF_8)

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
}