package org.thp.cortex.services

import java.nio.charset.StandardCharsets
import java.nio.file._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import akka.actor.ActorSystem
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import javax.inject.{Inject, Singleton}
import org.thp.cortex.models._

import org.elastic4play.utils.RichFuture

@Singleton
class DockerJobRunnerSrv(client: DockerClient, autoUpdate: Boolean, implicit val system: ActorSystem) {

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
      config.getOptional[Boolean]("docker.autoUpdate").getOrElse(true),
      system: ActorSystem
    )

  lazy val logger = Logger(getClass)

  lazy val isAvailable: Boolean =
    Try {
      logger.info(s"Docker is available:\n${client.info()}")
      true
    }.recover {
      case error ⇒
        logger.info(s"Docker is not available", error)
        false
    }.get

  def run(jobDirectory: Path, dockerImage: String, job: Job, timeout: Option[FiniteDuration])(implicit ec: ExecutionContext): Future[Unit] = {
    import scala.collection.JavaConverters._
    if (autoUpdate) client.pull(dockerImage)
    //    ContainerConfig.builder().addVolume()
    val hostConfig = HostConfig
      .builder()
      .appendBinds(
        Bind
          .from(jobDirectory.toAbsolutePath.toString)
          .to("/job")
          .readOnly(false)
          .build()
      )
      .build()
    val cacertsFile = jobDirectory.resolve("input").resolve("cacerts")
    val containerConfigBuilder = ContainerConfig
      .builder()
      .hostConfig(hostConfig)
      .image(dockerImage)
      .cmd("/job")

    val containerConfig =
      if (Files.exists(cacertsFile)) containerConfigBuilder.env(s"REQUESTS_CA_BUNDLE=/job/input/cacerts").build()
      else containerConfigBuilder.build()
    val containerCreation = client.createContainer(containerConfig)
    //          Option(containerCreation.warnings()).flatMap(_.asScala).foreach(logger.warn)
    logger.info(
      s"Execute container ${containerCreation.id()}\n" +
        s"  timeout: ${timeout.fold("none")(_.toString)}\n" +
        s"  image  : $dockerImage\n" +
        s"  volume : ${jobDirectory.toAbsolutePath}:/job" +
        Option(containerConfig.env()).fold("")(_.asScala.map("\n  env    : " + _).mkString)
    )

    val execution = Future {
      client.startContainer(containerCreation.id())
      client.waitContainer(containerCreation.id())
      ()
    }.andThen {
      case r ⇒
        if (!Files.exists(jobDirectory.resolve("output").resolve("output.json"))) {
          val message = r.fold(e ⇒ s"Docker creation error: ${e.getMessage}\n", _ ⇒ "") +
            Try(client.logs(containerCreation.id(), LogsParam.stdout(), LogsParam.stderr()).readFully())
              .recover { case e ⇒ s"Container logs can't be read (${e.getMessage}" }
          val report = Json.obj("success" → false, "errorMessage" → message)
          Files.write(jobDirectory.resolve("output").resolve("output.json"), report.toString.getBytes(StandardCharsets.UTF_8))
        }
    }
    timeout
      .fold(execution)(t ⇒ execution.withTimeout(t, client.stopContainer(containerCreation.id(), 3)))
      .andThen {
        case _ ⇒ client.removeContainer(containerCreation.id(), DockerClient.RemoveContainerParam.forceKill())
      }
  }

}
