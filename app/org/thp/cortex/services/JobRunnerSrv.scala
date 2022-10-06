package org.thp.cortex.services

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date
import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import play.api.libs.json._
import play.api.{Configuration, Logger}
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO

import javax.inject.Inject
import org.thp.cortex.models._
import org.elastic4play.BadRequestError
import org.elastic4play.controllers.{Fields, FileInputValue}
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services.{AttachmentSrv, AuthContext, CreateSrv, UpdateSrv}

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
    implicit val mat: Materializer
) {

  val logger: Logger                                   = Logger(getClass)
  lazy val analyzerExecutionContext: ExecutionContext  = akkaSystem.dispatchers.lookup("analyzer")
  lazy val responderExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("responder")
  val jobDirectory: Path                               = Paths.get(config.get[String]("job.directory"))
  private val globalKeepJobFolder: Boolean             = config.get[Boolean]("job.keepJobFolder")

  private val runners: Seq[String] = config
    .getOptional[Seq[String]]("job.runners")
    .getOrElse(Seq("docker", "process"))
    .map(_.toLowerCase)
    .collect {
      case "docker" if dockerJobRunnerSrv.isAvailable => "docker"
      case "process" =>
        Seq("", "2", "3").foreach { pythonVersion =>
          val cortexUtilsVersion = processJobRunnerSrv.checkCortexUtilsVersion(pythonVersion)
          cortexUtilsVersion.fold(logger.warn(s"The package cortexutils for python$pythonVersion hasn't been found")) {
            case (major, minor, patch) if major >= 2 =>
              logger.info(s"The package cortexutils for python$pythonVersion has valid version: $major.$minor.$patch")
            case (major, minor, patch) =>
              logger.error(
                s"The package cortexutils for python$pythonVersion has invalid version: $major.$minor.$patch. Cortex 2 requires cortexutils >= 2.0"
              )
          }
        }
        "process"
    }

  lazy val processRunnerIsEnable: Boolean = runners.contains("process")
  lazy val dockerRunnerIsEnable: Boolean  = runners.contains("docker")

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

  private def delete(directory: Path): Unit =
    try {
      if (Files.exists(directory))
        Files.walkFileTree(directory, deleteVisitor)
      ()
    } catch {
      case t: Throwable => logger.warn(s"Fail to remove temporary files ($directory) : $t")
    }

  private def prepareJobFolder(worker: Worker, job: Job): Try[Path] = {
    val jobFolder = Files.createTempDirectory(jobDirectory, s"cortex-job-${job.id}-")
    logger.debug(s"Job folder is $jobFolder")
    val inputJobFolder = Files.createDirectories(jobFolder.resolve("input"))
    Files.createDirectories(jobFolder.resolve("output"))

    job
      .attachment()
      .map { attachment =>
        val attachmentFile = Files.createTempFile(inputJobFolder, "attachment", "")
        Try(
          Await.result(
            attachmentSrv
              .source(attachment.id)
              .runWith(FileIO.toPath(attachmentFile)),
            10.minutes
          )
        )
          .map(_ => Some(attachmentFile))
      }
      .getOrElse(Success(None))
      .map {
        case Some(file) =>
          Json.obj("file" -> file.getFileName.toString, "filename" -> job.attachment().get.name, "contentType" -> job.attachment().get.contentType)
        case None if job.data().nonEmpty && job.tpe() == WorkerType.responder =>
          Json.obj("data" -> Json.parse(job.data().get))
        case None if job.data().nonEmpty && job.tpe() == WorkerType.analyzer =>
          Json.obj("data" -> job.data().get)
      }
      .map { artifact =>
        val proxy_http = (worker.config \ "proxy_http").asOpt[String].fold(JsObject.empty) { proxy =>
          Json.obj("proxy" -> Json.obj("http" -> proxy))
        }
        val proxy_https = (worker.config \ "proxy_https").asOpt[String].fold(JsObject.empty) { proxy =>
          Json.obj("proxy" -> Json.obj("https" -> proxy))
        }
        val config = workerSrv
          .getDefinition(worker.workerDefinitionId())
          .fold(_ => JsObject.empty, _.configuration)
          .deepMerge(worker.config)
          .deepMerge(proxy_http)
          .deepMerge(proxy_https)
        (worker.config \ "cacerts").asOpt[String].filterNot(_.trim.isEmpty).foreach { cacerts =>
          val cacertsFile = jobFolder.resolve("input").resolve("cacerts")
          Files.write(cacertsFile, cacerts.getBytes)
        }
        artifact +
          ("dataType"   -> JsString(job.dataType())) +
          ("tlp"        -> JsNumber(job.tlp())) +
          ("pap"        -> JsNumber(job.pap())) +
          ("message"    -> JsString(job.message().getOrElse(""))) +
          ("parameters" -> job.params) +
          ("config"     -> config)
      }
      .map { input =>
        logger.debug(s"Write worker input: $input")
        Files.write(inputJobFolder.resolve("input.json"), input.toString.getBytes(StandardCharsets.UTF_8))
        jobFolder
      }
      .recoverWith {
        case error =>
          if (!(job.params \ "keepJobFolder").asOpt[Boolean].contains(true) || globalKeepJobFolder)
            delete(jobFolder)
          Failure(error)
      }
  }

  private def extractReport(jobFolder: Path, job: Job)(implicit authContext: AuthContext): Future[Job] = {
    val outputFile = jobFolder.resolve("output").resolve("output.json")
    if (Files.exists(outputFile)) {
      logger.debug(s"Job output: ${new String(Files.readAllBytes(outputFile))}")
      val is = Files.newInputStream(outputFile)
      val report =
        try Json.parse(is)
        finally is.close()

      val success = (report \ "success").asOpt[Boolean].getOrElse(false)
      if (success) {
        val fullReport    = (report \ "full").as[JsObject].toString
        val summaryReport = (report \ "summary").asOpt[JsObject].getOrElse(JsObject.empty).toString
        val artifacts     = (report \ "artifacts").asOpt[Seq[JsObject]].getOrElse(Nil)
        val operations    = (report \ "operations").asOpt[Seq[JsObject]].getOrElse(Nil)
        val reportFields = Fields
          .empty
          .set("full", fullReport)
          .set("summary", summaryReport)
          .set("operations", JsArray(operations).toString)
        createSrv[ReportModel, Report, Job](reportModel, job, reportFields)
          .flatMap { report =>
            Future.sequence {
              for {
                artifact <- artifacts
                dataType <- (artifact \ "dataType").asOpt[String]
                fields <- dataType match {
                  case "file" =>
                    for {
                      name <- (artifact \ "filename").asOpt[String]
                      file <- (artifact \ "file").asOpt[String]
                      path = jobFolder.resolve("output").resolve(file)
                      if Files.exists(path) && path.getParent == jobFolder.resolve("output")
                      contentType = (artifact \ "contentType").asOpt[String].getOrElse("application/octet-stream")
                      fiv         = FileInputValue(name, path, contentType)
                    } yield Fields(artifact - "filename" - "file" - "contentType").set("attachment", fiv)
                  case _ => Some(Fields(artifact))
                }
              } yield createSrv[ArtifactModel, Artifact, Report](artifactModel, report, fields)
            }
          }
          .transformWith {
            case Failure(e) => endJob(job, JobStatus.Failure, Some(s"Report creation failure: $e"))
            case _          => endJob(job, JobStatus.Success)
          }
      } else
        endJob(job, JobStatus.Failure, (report \ "errorMessage").asOpt[String], (report \ "input").asOpt[JsValue].map(_.toString))
    } else
      endJob(job, JobStatus.Failure, Some(s"no output"))
  }

  def run(worker: Worker, job: Job)(implicit authContext: AuthContext): Future[Job] = {
    val executionContext = worker.tpe() match {
      case WorkerType.analyzer  => analyzerExecutionContext
      case WorkerType.responder => responderExecutionContext
    }
    var maybeJobFolder: Option[Path] = None

    Future {
      syncStartJob(job).get
      val jobFolder = prepareJobFolder(worker, job).get
      maybeJobFolder = Some(jobFolder)
      runners
        .foldLeft[Option[Try[Unit]]](None) {
          case (None, "docker") =>
            worker
              .dockerImage()
              .map(dockerImage => dockerJobRunnerSrv.run(jobFolder, dockerImage, worker.jobTimeout().map(_.minutes)))
              .orElse {
                logger.warn(s"worker ${worker.id} can't be run with docker (doesn't have image)")
                None
              }
          case (None, "process") =>
            worker
              .command()
              .map(command => processJobRunnerSrv.run(jobFolder, command, job, worker.jobTimeout().map(_.minutes)))
              .orElse {
                logger.warn(s"worker ${worker.id} can't be run with process (doesn't have command)")
                None
              }
          case (j: Some[_], _) => j
          case (None, runner) =>
            logger.warn(s"Unknown job runner: $runner")
            None

        }
        .getOrElse(throw BadRequestError("Worker cannot be run"))
    }(executionContext)
      .transformWith {
        case _: Success[_] =>
          extractReport(maybeJobFolder.get /* can't be none */, job)
        case Failure(error) =>
          endJob(job, JobStatus.Failure, Option(error.getMessage), maybeJobFolder.map(jf => readFile(jf.resolve("input").resolve("input.json"))))

      }
      .andThen {
        case _ =>
          if (!(job.params \ "keepJobFolder").asOpt[Boolean].contains(true) || globalKeepJobFolder)
            maybeJobFolder.foreach(delete)
      }
  }

  private def readFile(input: Path): String = new String(Files.readAllBytes(input), StandardCharsets.UTF_8)

  private def syncStartJob(job: Job)(implicit authContext: AuthContext): Try[Job] = {
    val fields = Fields
      .empty
      .set("status", JobStatus.InProgress.toString)
      .set("startDate", Json.toJson(new Date))
    Try(
      Await.result(updateSrv(job, fields, ModifyConfig(retryOnConflict = 0, seqNoAndPrimaryTerm = Some((job.seqNo, job.primaryTerm)))), 1.minute)
    )
  }

  private def endJob(job: Job, status: JobStatus.Type, errorMessage: Option[String] = None, input: Option[String] = None)(implicit
      authContext: AuthContext
  ): Future[Job] = {
    val fields = Fields
      .empty
      .set("status", status.toString)
      .set("endDate", Json.toJson(new Date))
      .set("input", input.map(JsString.apply))
      .set("errorMessage", errorMessage.map(JsString.apply))
    updateSrv(job, fields, ModifyConfig.default)
  }
}
