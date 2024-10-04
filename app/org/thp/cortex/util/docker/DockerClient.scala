package org.thp.cortex.util.docker

import com.github.dockerjava.api.model._
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig, DockerClientImpl}
import com.github.dockerjava.transport.DockerHttpClient
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import play.api.{Configuration, Logger}

import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.concurrent.Executors
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.Try

class DockerClient(config: Configuration) {
  private lazy val logger: Logger           = Logger(getClass.getName)
  private lazy val (dockerConf, httpClient) = getHttpClient
  private lazy val underlyingClient         = DockerClientImpl.getInstance(dockerConf, httpClient)

  def execute(containerId: String): Try[String] = Try {
    val startContainerCmd = underlyingClient.startContainerCmd(containerId)
    startContainerCmd.exec()
    val waitResult = underlyingClient
      .waitContainerCmd(containerId)
      .start()
      .awaitStatusCode()
    logger.info(s"container $containerId started and awaited with code: $waitResult")

    containerId
  }

  def prepare(image: String, jobDirectory: Path, jobBaseDirectory: Path, dockerJobBaseDirectory: Path, timeout: FiniteDuration): Try[String] = Try {
    logger.info(s"image $image pull result: ${pullImage(image, timeout)}")
    val containerCmd = underlyingClient
      .createContainerCmd(image)
      .withHostConfig(configure(jobDirectory, jobBaseDirectory, dockerJobBaseDirectory))
    if (Files.exists(jobDirectory.resolve("input").resolve("cacerts")))
      containerCmd.withEnv(s"REQUESTS_CA_BUNDLE=/job/input/cacerts")
    val containerResponse = containerCmd.exec()
    logger.info(
      s"about to start container ${containerResponse.getId}\n" +
        s"  timeout: ${timeout.toString}\n" +
        s"  image  : $image\n" +
        s"  volumes : ${jobDirectory.toAbsolutePath}"
    )
    if (containerResponse.getWarnings.nonEmpty) logger.warn(s"${containerResponse.getWarnings.mkString(", ")}")
    scheduleContainerTimeout(containerResponse.getId, timeout)

    containerResponse.getId
  }

  private def configure(jobDirectory: Path, jobBaseDirectory: Path, dockerJobBaseDirectory: Path): HostConfig = {
    val hostConfigMut = HostConfig
      .newHostConfig()
      .withBinds(
        Seq(
          new Bind(
            dockerJobBaseDirectory.resolve(jobBaseDirectory.relativize(jobDirectory)).toAbsolutePath.toString,
            new Volume(s"/job"),
            AccessMode.rw
          )
        ): _*
      )

    config.getOptional[Seq[String]]("docker.container.capAdd").map(_.map(Capability.valueOf)).foreach(hostConfigMut.withCapAdd(_: _*))
    config.getOptional[Seq[String]]("docker.container.capDrop").map(_.map(Capability.valueOf)).foreach(hostConfigMut.withCapDrop(_: _*))
    config.getOptional[String]("docker.container.cgroupParent").foreach(hostConfigMut.withCgroupParent)
    config.getOptional[Long]("docker.container.cpuPeriod").foreach(hostConfigMut.withCpuPeriod(_))
    config.getOptional[Long]("docker.container.cpuQuota").foreach(hostConfigMut.withCpuQuota(_))
    config.getOptional[Seq[String]]("docker.container.dns").map(_.asJava).foreach(hostConfigMut.withDns)
    config.getOptional[Seq[String]]("docker.container.dnsSearch").map(_.asJava).foreach(hostConfigMut.withDnsSearch)
    config.getOptional[Seq[String]]("docker.container.extraHosts").foreach(l => hostConfigMut.withExtraHosts(l: _*))
    config.getOptional[Long]("docker.container.kernelMemory").foreach(hostConfigMut.withKernelMemory(_))
    config.getOptional[Long]("docker.container.memoryReservation").foreach(hostConfigMut.withMemoryReservation(_))
    config.getOptional[Long]("docker.container.memory").foreach(hostConfigMut.withMemory(_))
    config.getOptional[Long]("docker.container.memorySwap").foreach(hostConfigMut.withMemorySwap(_))
    config.getOptional[Long]("docker.container.memorySwappiness").foreach(hostConfigMut.withMemorySwappiness(_))
    config.getOptional[String]("docker.container.networkMode").foreach(hostConfigMut.withNetworkMode)
    config.getOptional[Boolean]("docker.container.privileged").foreach(hostConfigMut.withPrivileged(_))

    hostConfigMut
  }

  def info: Info = underlyingClient.infoCmd().exec()
  def pullImage(image: String, timeout: FiniteDuration): Boolean = {
    val pullImageResultCbk = underlyingClient // Blocking
      .pullImageCmd(image)
      .start()
      .awaitCompletion()

    pullImageResultCbk.awaitCompletion(timeout.length, timeout.unit)
  }

  def clean(containerId: String): Try[Unit] = Try {
    underlyingClient
      .removeContainerCmd(containerId)
      .withForce(true)
      .exec()
    logger.info(s"removed container $containerId")
  }

  def getLogs(containerId: String): Try[String] = Try {
    val stringBuilder = new StringBuilder()
    val callback      = new DockerLogsStringBuilder(stringBuilder)
    underlyingClient
      .logContainerCmd(containerId)
      .withStdErr(true)
      .withStdOut(true)
      .withFollowStream(true)
      .withTailAll()
      .exec(callback)
      .awaitCompletion()

    callback.builder.toString
  }

  private def scheduleContainerTimeout(containerId: String, timeout: FiniteDuration) =
    Executors
      .newSingleThreadScheduledExecutor()
      .schedule(
        () => {
          logger.info(s"timeout $timeout reached, stopping container $containerId}")
          underlyingClient.removeContainerCmd(containerId).withForce(true).exec()
        },
        timeout.length,
        timeout.unit
      )

  private def getHttpClient: (DockerClientConfig, DockerHttpClient) = {
    val dockerConf = getBaseConfig

    (
      dockerConf,
      new ZerodepDockerHttpClient.Builder()
        .dockerHost(dockerConf.getDockerHost)
        .sslConfig(dockerConf.getSSLConfig)
        .maxConnections(if (config.has("docker.httpClient.maxConnections")) config.get[Int]("docker.httpClient.maxConnections") else 100)
        .connectionTimeout(
          if (config.has("docker.httpClient.connectionTimeout")) Duration.ofMillis(config.get[Long]("docker.httpClient.connectionTimeout"))
          else Duration.ofSeconds(30)
        )
        .responseTimeout(
          if (config.has("docker.httpClient.responseTimeout")) Duration.ofMillis(config.get[Long]("docker.httpClient.responseTimeout"))
          else Duration.ofSeconds(45)
        )
        .build()
    )
  }

  private def getBaseConfig: DockerClientConfig = {
    val confBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
    if (config.has("docker")) {
      if (config.has("docker.host")) confBuilder.withDockerHost(config.get[String]("docker.host"))
      if (config.has("docker.tlsVerify")) confBuilder.withDockerTlsVerify(config.get[Boolean]("docker.tlsVerify"))
      if (config.has("docker.certPath")) confBuilder.withDockerCertPath(config.get[String]("docker.certPath"))
      if (config.has("docker.registry")) {
        if (config.has("docker.registry.user")) confBuilder.withRegistryUsername(config.get[String]("docker.registry.user"))
        if (config.has("docker.registry.password")) confBuilder.withRegistryPassword(config.get[String]("docker.registry.password"))
        if (config.has("docker.registry.email")) confBuilder.withRegistryEmail(config.get[String]("docker.registry.email"))
        if (config.has("docker.registry.url")) confBuilder.withRegistryUrl(config.get[String]("docker.registry.url"))
      }
    }

    confBuilder.build()
  }
}
