package org.thp.cortex.services

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class JobRunnerSrvSpec extends PlaySpecification {

  "JobRunnerSrv.applyImagePrefix" should {

    "return the original image when prefix is empty" in {
      JobRunnerSrv.applyImagePrefix("", "cortexneurons/abuseipdb:1") must_== "cortexneurons/abuseipdb:1"
    }

    "prepend the prefix to the image" in {
      JobRunnerSrv.applyImagePrefix("harbor.example.com/docker.io/", "cortexneurons/abuseipdb:1") must_==
        "harbor.example.com/docker.io/cortexneurons/abuseipdb:1"
    }

    "prepend the prefix to an image that already includes docker.io" in {
      JobRunnerSrv.applyImagePrefix("harbor.example.com/", "docker.io/cortexneurons/abuseipdb:1") must_==
        "harbor.example.com/docker.io/cortexneurons/abuseipdb:1"
    }

    "handle prefix without trailing slash" in {
      JobRunnerSrv.applyImagePrefix("harbor.example.com", "cortexneurons/abuseipdb:1") must_==
        "harbor.example.comcortexneurons/abuseipdb:1"
    }
  }
}
