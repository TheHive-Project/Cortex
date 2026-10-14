package org.thp.cortex.services.auth.oauth2

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class PkceSpec extends PlaySpecification {

  "Pkce.computeCodeChallenge" should {

    "compute the S256 challenge of a code verifier" in {
      // GIVEN - the code verifier from RFC 7636, Appendix B
      val codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

      // WHEN - its challenge is computed
      val challenge = Pkce.computeCodeChallenge(codeVerifier)

      // THEN - it is the code challenge from RFC 7636, Appendix B
      challenge must_== "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    }
  }

  "Pkce.generateCodeVerifier" should {

    "generate a code verifier compliant with RFC 7636" in {
      // WHEN - a code verifier is generated
      val codeVerifier = Pkce.generateCodeVerifier()

      // THEN - it complies with RFC 7636 section 4.1: 43 to 128 characters from [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
      codeVerifier must beMatching("[A-Za-z0-9\\-._~]{43,128}")
    }

    "generate a different code verifier on each call" in {
      // WHEN - two code verifiers are generated
      val first  = Pkce.generateCodeVerifier()
      val second = Pkce.generateCodeVerifier()

      // THEN - they are different
      first must_!= second
    }
  }
}
