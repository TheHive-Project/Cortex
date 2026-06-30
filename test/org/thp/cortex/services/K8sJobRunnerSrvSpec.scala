package org.thp.cortex.services

import com.typesafe.config.ConfigFactory
import io.fabric8.kubernetes.api.model.PodSecurityContext
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.Configuration
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class K8sJobRunnerSrvSpec extends PlaySpecification {

  "K8sJobRunnerSrv.parseLabels" should {

    "parse HOCON keys with slashes and dots without extra quotes" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """job.kubernetes.labels {
            |  "app.kubernetes.io/instance" = "cortex"
            |  "app.kubernetes.io/managed-by" = "Helm"
            |}""".stripMargin
        )
      )
      val labels = K8sJobRunnerSrv.parseLabels(config)
      labels must_== Map("app.kubernetes.io/instance" -> "cortex", "app.kubernetes.io/managed-by" -> "Helm")
      forall(labels.keys)(k => k must not(startWith("\"")))
    }

    "return empty map when labels config is absent" in {
      val config = Configuration(ConfigFactory.parseString("job.kubernetes {}"))
      K8sJobRunnerSrv.parseLabels(config) must_== Map.empty[String, String]
    }

    "return empty map when labels block is empty" in {
      val config = Configuration(ConfigFactory.parseString("job.kubernetes.labels {}"))
      K8sJobRunnerSrv.parseLabels(config) must_== Map.empty[String, String]
    }
  }

  "K8sJobRunnerSrv.buildJobLabels" should {

    val cortexLabels = Map(
      "cortex-job-id"     -> "abc123",
      "cortex-worker-id"  -> "worker1",
      "cortex-neuron-job" -> "true"
    )

    "return only cortex labels when extra labels is empty" in {
      K8sJobRunnerSrv.buildJobLabels(cortexLabels, Map.empty) must_== cortexLabels
    }

    "merge extra labels with cortex labels" in {
      val extra = Map(
        "app.kubernetes.io/instance"   -> "cortex",
        "app.kubernetes.io/managed-by" -> "Helm"
      )
      val result = K8sJobRunnerSrv.buildJobLabels(cortexLabels, extra)
      result must_== cortexLabels ++ extra
    }

    "allow extra labels to override cortex labels" in {
      val extra  = Map("cortex-neuron-job" -> "custom-value")
      val result = K8sJobRunnerSrv.buildJobLabels(cortexLabels, extra)
      result("cortex-neuron-job") must_== "custom-value"
    }

    "preserve all cortex labels when extra labels don't overlap" in {
      val extra  = Map("team" -> "soc")
      val result = K8sJobRunnerSrv.buildJobLabels(cortexLabels, extra)
      result("cortex-job-id") must_== "abc123"
      result("cortex-worker-id") must_== "worker1"
      result("cortex-neuron-job") must_== "true"
      result("team") must_== "soc"
    }
  }

  "K8sJobRunnerSrv.parsePodSecurityContext" should {

    "return None when securityContext config is absent" in {
      val config = Configuration(ConfigFactory.parseString("job.kubernetes {}"))
      K8sJobRunnerSrv.parsePodSecurityContext(config) must beNone
    }

    "parse runAsUser, runAsGroup, and fsGroup" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """job.kubernetes.securityContext {
            |  runAsUser = 1000
            |  runAsGroup = 1000
            |  fsGroup = 1000
            |}""".stripMargin
        )
      )
      val ctx = K8sJobRunnerSrv.parsePodSecurityContext(config)
      ctx must beSome[PodSecurityContext]
      ctx.get.getRunAsUser must_== 1000L
      ctx.get.getRunAsGroup must_== 1000L
      ctx.get.getFsGroup must_== 1000L
    }

    "parse runAsNonRoot" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """job.kubernetes.securityContext {
            |  runAsNonRoot = true
            |}""".stripMargin
        )
      )
      val ctx = K8sJobRunnerSrv.parsePodSecurityContext(config)
      ctx must beSome[PodSecurityContext]
      ctx.get.getRunAsNonRoot must_== true
    }

    "handle partial config with only runAsUser" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """job.kubernetes.securityContext {
            |  runAsUser = 65534
            |}""".stripMargin
        )
      )
      val ctx = K8sJobRunnerSrv.parsePodSecurityContext(config)
      ctx must beSome[PodSecurityContext]
      ctx.get.getRunAsUser must_== 65534L
      ctx.get.getRunAsGroup must beNull
      ctx.get.getFsGroup must beNull
    }

    "return None when securityContext block is empty" in {
      val config = Configuration(ConfigFactory.parseString("job.kubernetes.securityContext {}"))
      K8sJobRunnerSrv.parsePodSecurityContext(config) must beNone
    }

    "still build the context when an unknown key is present (ignored with a warning)" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """job.kubernetes.securityContext {
            |  runAsUser = 1000
            |  unknownField = "something"
            |}""".stripMargin
        )
      )
      val ctx = K8sJobRunnerSrv.parsePodSecurityContext(config)
      ctx must beSome[PodSecurityContext]
      ctx.get.getRunAsUser must_== 1000L
    }

    "still build the context when given a negative UID (logs a warning but does not reject)" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """job.kubernetes.securityContext {
            |  runAsUser = -1
            |}""".stripMargin
        )
      )
      val ctx = K8sJobRunnerSrv.parsePodSecurityContext(config)
      ctx must beSome[PodSecurityContext]
      ctx.get.getRunAsUser must_== -1L
    }
  }
}
