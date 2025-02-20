package org.thp.cortex.services

import akka.actor.ActorSystem
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.batch.{JobBuilder => KJobBuilder}
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.thp.cortex.models._
import org.thp.cortex.util.FunctionalCondition._
import play.api.{Configuration, Logger}

import java.nio.file._
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class K8sJobRunnerSrv(
    client: DefaultKubernetesClient,
    jobBaseDirectory: Path,
    persistentVolumeClaimName: Option[String], // if not provided k8s runner is unavailable
    implicit val system: ActorSystem
) {

  @Inject()
  def this(config: Configuration, system: ActorSystem) =
    this(
      new DefaultKubernetesClient(),
      Paths.get(config.get[String]("job.directory")),
      config.getOptional[String]("job.kubernetes.persistentVolumeClaimName"),
      system: ActorSystem
    )

  lazy val logger: Logger = Logger(getClass)

  lazy val isAvailable: Boolean =
    Try {
      val ver = client.getVersion
      logger.info(s"Kubernetes is available: major ${ver.getMajor} minor ${ver.getMinor} git ${ver.getGitCommit}")
    } match {
      case _: Success[_] if persistentVolumeClaimName.isDefined => true
      case _: Success[_]  =>
        logger.info(s"Kubernetes is not available because no persistent volume claim is provided. " +
          "Please add `job.kubernetes.persistentVolumeClaimName=...` in the configuration")
        false
      case Failure(error) =>
        logger.info(s"Kubernetes is not available", error)
        false
    }

  def run(jobDirectory: Path, dockerImage: String, job: Job, timeout: Option[FiniteDuration]): Try[Unit] = {
    val cacertsFile = jobDirectory.resolve("input").resolve("cacerts")
    val relativeJobDirectory = jobBaseDirectory.relativize(jobDirectory).toString
    // make the default longer than likely values, but still not infinite
    val timeout_or_default = timeout.getOrElse(8.hours)
    // https://kubernetes.io/docs/concepts/overview/working-with-objects/names/
    // FIXME: this collapses case, jeopardizing the uniqueness of the identifier.
    //  LDH: lowercase, digits, hyphens.
    val ldh_jobid = job.id.toLowerCase().replace('_', '-')
    val kjobName = "neuron-job-" + ldh_jobid
    val pvcvs = new PersistentVolumeClaimVolumeSourceBuilder()
      .withClaimName(persistentVolumeClaimName.get)
      .withReadOnly(false)
      .build()

    val kjob = new KJobBuilder()
      .withApiVersion("batch/v1")
      .withNewMetadata()
      .withName(kjobName)
      .withLabels(Map(
        "cortex-job-id" -> job.id,
        "cortex-worker-id" -> job.workerId(),
        "cortex-neuron-job" -> "true").asJava)
      .endMetadata()
      .withNewSpec()
      .withNewTemplate()
      .withNewSpec()
      .addNewVolume()
      .withName("job-directory")
      .withPersistentVolumeClaim(pvcvs)
      .endVolume()
      .addNewContainer()
      .withName("neuron")
      .withImage(dockerImage)
      .withArgs("/job")
      .addNewEnv()
      .withName("CORTEX_JOB_FOLDER")
      .withValue(relativeJobDirectory)
      .endEnv()
      .when(Files.exists(cacertsFile))(
      _.addNewEnv()
        .withName("REQUESTS_CA_BUNDLE")
        .withValue("/job/input/cacerts")
        .endEnv()
      )
      .addNewVolumeMount()
      .withName("job-directory")
      .withSubPathExpr("$(CORTEX_JOB_FOLDER)/input")
      .withMountPath("/job/input")
      .withReadOnly(true)
      .endVolumeMount()
      .addNewVolumeMount()
      .withName("job-directory")
      .withSubPathExpr("$(CORTEX_JOB_FOLDER)/output")
      .withMountPath("/job/output")
      .withReadOnly(false)
      .endVolumeMount()
      .endContainer()
      .withRestartPolicy("Never")
      .endSpec()
    .endTemplate()
    .endSpec()
    .build()

    val execution = Try {
      val created_kjob = client.batch().jobs().create(kjob)
      val created_env = created_kjob
        .getSpec.getTemplate.getSpec.getContainers.get(0)
        .getEnv.asScala
      logger.info(
        s"Created Kubernetes Job ${created_kjob.getMetadata.getName}\n" +
        s"  timeout: ${timeout_or_default.toString}\n" +
        s"  image  : $dockerImage\n" +
        s"  mount  : pvc $persistentVolumeClaimName subdir $relativeJobDirectory as /job" +
        created_env.map(ev => s"\n  env    : ${ev.getName} = ${ev.getValue}").mkString)
      val ended_kjob = client.batch().jobs().withLabel("cortex-job-id", job.id)
        .waitUntilCondition(x => Option(x).flatMap(j =>
          Option(j.getStatus).flatMap(s =>
            Some(s.getConditions.asScala.map(_.getType).exists(t =>
              t.equals("Complete") || t.equals("Failed")))))
          .getOrElse(false),
          timeout_or_default.length, timeout_or_default.unit)
      if(ended_kjob != null) {
        logger.info(s"Kubernetes Job ${ended_kjob.getMetadata.getName} " +
          s"(for job ${job.id}) status is now ${ended_kjob.getStatus.toString}")
      } else {
        logger.info(s"Kubernetes Job for ${job.id} no longer exists")
      }
    }
    // let's find the job by the attribute we know is fundamentally
    // unique, rather than one constructed from it
    val deleted = client.batch().jobs().withLabel("cortex-job-id", job.id).delete()
    if(deleted) {
      logger.info(s"Deleted Kubernetes Job for job ${job.id}")
    } else {
      logger.info(s"While trying to delete Kubernetes Job for ${job.id}, the job was not found; this is OK")
    }
    execution
  }
}
