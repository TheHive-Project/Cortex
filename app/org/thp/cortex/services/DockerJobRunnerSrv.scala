package org.thp.cortex.services

import akka.actor.ActorSystem
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import java.nio.charset.StandardCharsets
import java.nio.file._
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

@Singleton
class DockerJobRunnerSrv(
    client: DockerClient,
    config: Configuration,
    autoUpdate: Boolean,
    jobBaseDirectory: Path,
    dockerJobBaseDirectory: Path,
    implicit val system: ActorSystem
) {

  @Inject()
  def this(config: Configuration, system: ActorSystem) =
    this(
      new DefaultDockerClient.Builder()
        .apiVersion(config.getOptional[String]("docker.version").orNull)
        .connectionPoolSize(config.getOptional[Int]("docker.connectionPoolSize").getOrElse(100))
        .connectTimeoutMillis(config.getOptional[Long]("docker.connectTimeoutMillis").getOrElse(5000))
        //.dockerCertificates()
        .readTimeoutMillis(config.getOptional[Long]("docker.readTimeoutMillis").getOrElse(30000))
        //.registryAuthSupplier()
        .uri(config.getOptional[String]("docker.uri").getOrElse("unix:///var/run/docker.sock"))
        .useProxy(config.getOptional[Boolean]("docker.useProxy").getOrElse(false))
        .build(),
      config,
      config.getOptional[Boolean]("docker.autoUpdate").getOrElse(true),
      Paths.get(config.get[String]("job.directory")),
      Paths.get(config.get[String]("job.dockerDirectory")),
      system: ActorSystem
    )

  lazy val logger: Logger = Logger(getClass)

  lazy val isAvailable: Boolean =
    Try {
      logger.debug(s"Retrieve docker information ...")
      logger.info(s"Docker is available:\n${client.info()}")
      true
    }.recover {
      case error =>
        logger.info(s"Docker is not available", error)
        false
    }.get

  def run(jobDirectory: Path, dockerImage: String, timeout: Option[FiniteDuration])(implicit
      ec: ExecutionContext
  ): Try[Unit] = {
    import scala.collection.JavaConverters._
    if (autoUpdate) Try(client.pull(dockerImage))
    //    ContainerConfig.builder().addVolume()
    val hostConfigBuilder = HostConfig.builder()
    config.getOptional[Seq[String]]("docker.container.capAdd").map(_.asJava).foreach(hostConfigBuilder.capAdd)
    config.getOptional[Seq[String]]("docker.container.capDrop").map(_.asJava).foreach(hostConfigBuilder.capDrop)
    config.getOptional[String]("docker.container.cgroupParent").foreach(hostConfigBuilder.cgroupParent)
    config.getOptional[Long]("docker.container.cpuPeriod").foreach(hostConfigBuilder.cpuPeriod(_))
    config.getOptional[Long]("docker.container.cpuQuota").foreach(hostConfigBuilder.cpuQuota(_))
    config.getOptional[Seq[String]]("docker.container.dns").map(_.asJava).foreach(hostConfigBuilder.dns)
    config.getOptional[Seq[String]]("docker.container.dnsSearch").map(_.asJava).foreach(hostConfigBuilder.dnsSearch)
    config.getOptional[Seq[String]]("docker.container.extraHosts").map(_.asJava).foreach(hostConfigBuilder.extraHosts)
    config.getOptional[Long]("docker.container.kernelMemory").foreach(hostConfigBuilder.kernelMemory(_))
    config.getOptional[Long]("docker.container.memoryReservation").foreach(hostConfigBuilder.memoryReservation(_))
    config.getOptional[Long]("docker.container.memory").foreach(hostConfigBuilder.memory(_))
    config.getOptional[Long]("docker.container.memorySwap").foreach(hostConfigBuilder.memorySwap(_))
    config.getOptional[Int]("docker.container.memorySwappiness").foreach(hostConfigBuilder.memorySwappiness(_))
    config.getOptional[String]("docker.container.networkMode").foreach(hostConfigBuilder.networkMode)
    config.getOptional[Boolean]("docker.container.privileged").foreach(hostConfigBuilder.privileged(_))
    hostConfigBuilder.appendBinds(
      Bind
        .from(dockerJobBaseDirectory.resolve(jobBaseDirectory.relativize(jobDirectory)).toAbsolutePath.toString)
        .to("/job")
        .readOnly(false)
        .build()
    )
    val cacertsFile = jobDirectory.resolve("input").resolve("cacerts")
    val containerConfigBuilder = ContainerConfig
      .builder()
      .hostConfig(hostConfigBuilder.build())
      .image(dockerImage)
      .cmd("/job")

    val containerConfig =
      if (Files.exists(cacertsFile)) containerConfigBuilder.env(s"REQUESTS_CA_BUNDLE=/job/input/cacerts").build()
      else containerConfigBuilder.build()
    val containerCreation = client.createContainer(containerConfig)
    //          Option(containerCreation.warnings()).flatMap(_.asScala).foreach(logger.warn)

    logger.debug(s"Container configuration: $containerConfig")
    logger.info(
      s"Execute container ${containerCreation.id()}\n" +
        s"  timeout: ${timeout.fold("none")(_.toString)}\n" +
        s"  image  : $dockerImage\n" +
        s"  volume : ${jobDirectory.toAbsolutePath}:/job" +
        Option(containerConfig.env()).fold("")(_.asScala.map("\n  env    : " + _).mkString)
    )

    val timeoutSched = timeout.map(to =>
      system.scheduler.scheduleOnce(to) {
        logger.info("Timeout reached, stopping the container")
        client.removeContainer(containerCreation.id(), DockerClient.RemoveContainerParam.forceKill())
      }
    )
    val execution = Try {
      client.startContainer(containerCreation.id())
      client.waitContainer(containerCreation.id())
      ()
    }
    timeoutSched.foreach(_.cancel())
    val outputFile = jobDirectory.resolve("output").resolve("output.json")
    if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
      logger.warn(s"The worker didn't generate output file.")
      val output = Try(client.logs(containerCreation.id(), LogsParam.stdout(), LogsParam.stderr()).readFully())
        .fold(e => s"Container logs can't be read (${e.getMessage})", identity)
      val message = execution.fold(e => s"Docker creation error: ${e.getMessage}\n$output", _ => output)

      val report = Json.obj("success" -> false, "errorMessage" -> message)
      Files.write(outputFile, report.toString.getBytes(StandardCharsets.UTF_8))
    }
    client.removeContainer(containerCreation.id(), DockerClient.RemoveContainerParam.forceKill())
    execution
  }

}
