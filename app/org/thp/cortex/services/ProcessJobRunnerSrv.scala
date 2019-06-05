package org.thp.cortex.services

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessLogger}

import play.api.Logger

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.thp.cortex.models._

import org.elastic4play.utils.RichFuture
import scala.sys.process._
import scala.util.Try

import play.api.libs.json.Json

@Singleton
class ProcessJobRunnerSrv @Inject()(implicit val system: ActorSystem) {

  lazy val logger = Logger(getClass)

  private val pythonPackageVersionRegex = "^Version: ([0-9]*)\\.([0-9]*)\\.([0-9]*)".r

  def checkCortexUtilsVersion(pythonVersion: String): Option[(Int, Int, Int)] =
    Try {
      (s"pip$pythonVersion" :: "show" :: "cortexutils" :: Nil)
        .lineStream
        .collectFirst {
          case pythonPackageVersionRegex(major, minor, patch) ⇒ (major.toInt, minor.toInt, patch.toInt)
        }
    }.getOrElse(None)

  def run(jobDirectory: Path, command: String, job: Job, timeout: Option[FiniteDuration])(implicit ec: ExecutionContext): Future[Unit] = {
    val baseDirectory = Paths.get(command).getParent.getParent
    logger.info(s"Execute $command in $baseDirectory, timeout is ${timeout.fold("none")(_.toString)}")
    val process = Process(Seq(command, jobDirectory.toString), baseDirectory.toFile)
      .run(ProcessLogger(s ⇒ logger.info(s"  Job ${job.id}: $s")))
    val execution = Future
      .apply {
        process.exitValue()
        ()
      }
      .recoverWith {
        case error ⇒
          logger.error(s"Execution of command $command failed", error)
          Future.apply {
            val report = Json.obj("success" → false, "errorMessage" → error.getMessage)
            Files.write(jobDirectory.resolve("output").resolve("output.json"), report.toString.getBytes(StandardCharsets.UTF_8))
            ()
          }
      }
    timeout.fold(execution)(t ⇒ execution.withTimeout(t, killProcess(process)))
  }

  def killProcess(process: Process): Unit = {
    logger.info("Timeout reached, killing process")
    process.destroy()
  }
}
