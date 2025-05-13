package org.thp.cortex.services

import akka.actor.ActorSystem
import org.thp.cortex.util.docker.DockerClient
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import java.nio.charset.StandardCharsets
import java.nio.file._
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

@Singleton
class DockerJobRunnerSrv(
    dockerClient: DockerClient,
    autoUpdate: Boolean,
    jobBaseDirectory: Path,
    dockerJobBaseDirectory: Path,
    implicit val system: ActorSystem
) {

  @Inject()
  def this(config: Configuration, system: ActorSystem) =
    this(
      new DockerClient(config),
      config.getOptional[Boolean]("docker.autoUpdate").getOrElse(true),
      Paths.get(config.get[String]("job.directory")),
      Paths.get(config.get[String]("job.dockerDirectory")),
      system: ActorSystem
    )

  lazy val logger: Logger = Logger(getClass)

  lazy val isAvailable: Boolean =
    Try {
      logger.debug(s"Retrieve docker information ...")
      logger.info(s"Docker is available:\n${dockerClient.info}")
      true
    }.recover {
      case error =>
        logger.info(s"Docker is not available", error)
        false
    }.get

  private def generateErrorOutput(containerId: String, f: Path) = {
    logger.warn(s"the runner didn't generate any output file $f")
    for {
      output <- dockerClient.getLogs(containerId)
      report = Json.obj("success" -> false, "errorMessage" -> output)
      _ <- Try(Files.write(f, report.toString.getBytes(StandardCharsets.UTF_8)))
    } yield report
  }

  def run(jobDirectory: Path, dockerImage: String, timeout: Option[FiniteDuration])(implicit executionContext: ExecutionContext): Try[Unit] = {
    val to = timeout.getOrElse(FiniteDuration(5000, TimeUnit.SECONDS))

    if (autoUpdate) dockerClient.pullImage(dockerImage)

    for {
      containerId <- dockerClient.prepare(dockerImage, jobDirectory, jobBaseDirectory, dockerJobBaseDirectory, to)
      timeoutScheduled = timeout.map(to =>
        system.scheduler.scheduleOnce(to) {
          logger.info("Timeout reached, stopping the container")
          dockerClient.clean(containerId)
        }
      )
      _ <- dockerClient.execute(containerId)
      _ = timeoutScheduled.foreach(_.cancel())
      outputFile <- Try(jobDirectory.resolve("output").resolve("output.json"))
      isError = Files.notExists(outputFile) || Files.size(outputFile) == 0 || Files.isDirectory(outputFile)
      _       = if (isError) generateErrorOutput(containerId, outputFile).toOption else None
      _ <- dockerClient.clean(containerId)
    } yield ()
  }

}
