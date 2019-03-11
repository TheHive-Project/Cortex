package org.thp.cortex.services

import java.nio.file._

import scala.concurrent.{ ExecutionContext, Future }

import play.api.{ Configuration, Logger }

import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages.{ ContainerConfig, HostConfig }
import com.spotify.docker.client.{ DefaultDockerClient, DockerClient }
import javax.inject.{ Inject, Singleton }
import org.thp.cortex.models._

@Singleton
class DockerJobRunnerSrv(client: DockerClient, autoUpdate: Boolean) {

  def this() = this(DefaultDockerClient.fromEnv().build(), false)

  @Inject()
  def this(config: Configuration) = this(
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
    config.getOptional[Boolean]("docker.autoUpdate").getOrElse(true))

  lazy val logger = Logger(getClass)

  def run(jobDirectory: Path, dockerImage: String, job: Job)(implicit ec: ExecutionContext): Future[Unit] = {
    import scala.collection.JavaConverters._
    //    client.pull(dockerImage)
    //    ContainerConfig.builder().addVolume()
    val hostConfig = HostConfig.builder()
      .appendBinds(Bind.from(jobDirectory.toAbsolutePath.toString)
        .to("/job")
        .readOnly(false)
        .build())
      .build()
    val cacertsFile = jobDirectory.resolve("input").resolve("cacerts")
    val containerConfigBuilder = ContainerConfig
      .builder()
      .hostConfig(hostConfig)
      .image(dockerImage)
      .cmd("/job")

    val containerConfig = if (Files.exists(cacertsFile)) containerConfigBuilder.env(s"REQUESTS_CA_BUNDLE=/job/input/cacerts").build()
    else containerConfigBuilder.build()
    val containerCreation = client.createContainer(containerConfig)
    //          Option(containerCreation.warnings()).flatMap(_.asScala).foreach(logger.warn)
    logger.info(s"Execute container ${containerCreation.id()}\n" +
      s"  image : $dockerImage\n" +
      s"  volume: ${jobDirectory.toAbsolutePath}:/job" +
      Option(containerConfig.env()).fold("")(_.asScala.map("\n  env   : " + _).mkString))

    client.startContainer(containerCreation.id())

    Future {
      client.waitContainer(containerCreation.id())
      ()
    }
  }

}
