package org.thp.cortex.services

import akka.actor.ActorSystem
import org.thp.cortex.models._
import play.api.Logger
import play.api.libs.json.Json

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import javax.inject.{Inject, Singleton}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.sys.process.{Process, ProcessLogger, _}
import scala.util.Try

@Singleton
class ProcessJobRunnerSrv @Inject() (implicit val system: ActorSystem) {

  lazy val logger: Logger = Logger(getClass)

  private val pythonPackageVersionRegex = "^Version: (\\d*)\\.(\\d*)\\.(\\d*)".r

  def checkCortexUtilsVersion(pythonVersion: String): Option[(Int, Int, Int)] =
    Try {
      (s"pip$pythonVersion" :: "show" :: "cortexutils" :: Nil)
        .lineStream
        .collectFirst {
          case pythonPackageVersionRegex(major, minor, patch) => (major.toInt, minor.toInt, patch.toInt)
        }
    }.getOrElse(None)

  def run(jobDirectory: Path, command: String, job: Job, timeout: Option[FiniteDuration])(implicit
      ec: ExecutionContext
  ): Try[Unit] = {
    val baseDirectory = Paths.get(command).getParent.getParent
    val output        = mutable.StringBuilder.newBuilder
    logger.info(s"Execute $command in $baseDirectory, timeout is ${timeout.fold("none")(_.toString)}")
    val cacertsFile = jobDirectory.resolve("input").resolve("cacerts")
    val env         = if (Files.exists(cacertsFile)) Seq("REQUESTS_CA_BUNDLE" -> cacertsFile.toString) else Nil
    val process = Process(Seq(command, jobDirectory.toString), baseDirectory.toFile, env: _*)
      .run(ProcessLogger { s =>
        logger.info(s"  Job ${job.id}: $s")
        output ++= s
      })
    val timeoutSched = timeout.map(to =>
      system.scheduler.scheduleOnce(to) {
        logger.info("Timeout reached, killing process")
        process.destroy()
      }
    )

    val execution = Try {
      process.exitValue()
      ()
    }
    timeoutSched.foreach(_.cancel())
    val outputFile = jobDirectory.resolve("output").resolve("output.json")
    if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
      logger.warn(s"The worker didn't generate output file, use output stream.")
      val message = execution.fold(e => s"Process execution error: ${e.getMessage}\n$output.result()", _ => output.result())
      val report  = Json.obj("success" -> false, "errorMessage" -> message)
      Files.write(outputFile, report.toString.getBytes(StandardCharsets.UTF_8))
    }
    execution
  }

}
