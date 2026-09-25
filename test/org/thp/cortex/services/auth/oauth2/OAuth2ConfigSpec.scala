package org.thp.cortex.services.auth.oauth2

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.Configuration
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class OAuth2ConfigSpec extends PlaySpecification {

  private val baseConfig: Map[String, Any] = Map(
    "auth.oauth2.clientId"         -> "client-id",
    "auth.oauth2.clientSecret"     -> "client-secret",
    "auth.oauth2.redirectUri"      -> "https://cortex.example/api/ssoLogin",
    "auth.oauth2.responseType"     -> "code",
    "auth.oauth2.authorizationUrl" -> "https://idp.example/authorize",
    "auth.oauth2.tokenUrl"         -> "https://idp.example/token",
    "auth.oauth2.userUrl"          -> "https://idp.example/userinfo",
    "auth.oauth2.scope"            -> Seq("openid", "profile")
  )

  "OAuth2Config" should {

    "read the OAuth2 configuration when all the required keys are set" in {
      // GIVEN - a configuration with all the required OAuth2 keys
      val configuration = Configuration.from(baseConfig)

      // WHEN - the OAuth2 configuration is read
      val oauth2Config = OAuth2Config(configuration)

      // THEN - OAuth2 is configured with these values, and the optional keys get their defaults
      oauth2Config.map(_.clientId) must beSome("client-id")
      oauth2Config.map(_.clientSecret) must beSome("client-secret")
      oauth2Config.map(_.tokenUrl) must beSome("https://idp.example/token")
      oauth2Config.map(_.scope) must beSome(Seq("openid", "profile"))
      oauth2Config.map(_.authorizationHeader) must beSome("Bearer")
    }

    "disable OAuth2 when a required key is missing" in {
      // GIVEN - a configuration without the token URL
      val configuration = Configuration.from(baseConfig - "auth.oauth2.tokenUrl")

      // WHEN - the OAuth2 configuration is read
      val oauth2Config = OAuth2Config(configuration)

      // THEN - OAuth2 is not configured
      oauth2Config must beNone
    }
  }
}
