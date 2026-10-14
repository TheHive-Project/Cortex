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

    "enable only the user creation when only auth.sso.autocreate is set" in {
      // GIVEN - a configuration asking to create unknown users, but not to update existing ones
      val configuration = Configuration.from(baseConfig + ("auth.sso.autocreate" -> true) + ("auth.sso.autoupdate" -> false))

      // WHEN - the OAuth2 configuration is read
      val oauth2Config = OAuth2Config(configuration)

      // THEN - unknown users are created, existing users are not updated
      oauth2Config.map(_.autocreate) must beSome(true)
      oauth2Config.map(_.autoupdate) must beSome(false)
    }

    "enable only the user update when only auth.sso.autoupdate is set" in {
      // GIVEN - a configuration asking to update existing users, but not to create unknown ones
      val configuration = Configuration.from(baseConfig + ("auth.sso.autocreate" -> false) + ("auth.sso.autoupdate" -> true))

      // WHEN - the OAuth2 configuration is read
      val oauth2Config = OAuth2Config(configuration)

      // THEN - existing users are updated, unknown users are not created
      oauth2Config.map(_.autoupdate) must beSome(true)
      oauth2Config.map(_.autocreate) must beSome(false)
    }

    "disable PKCE when not set in configuration" in {
      // GIVEN - a valid OAuth2 configuration without the pkce key
      val configuration = Configuration.from(baseConfig)

      // WHEN - the OAuth2 configuration is read
      val oauth2Config = OAuth2Config(configuration)

      // THEN - PKCE is disabled
      oauth2Config.map(_.usePkce) must beSome(false)
    }

    "enable PKCE when set to true in configuration" in {
      // GIVEN - a valid OAuth2 configuration with pkce = true
      val configuration = Configuration.from(baseConfig + ("auth.oauth2.pkce" -> true))

      // WHEN - the OAuth2 configuration is read
      val oauth2Config = OAuth2Config(configuration)

      // THEN - PKCE is enabled
      oauth2Config.map(_.usePkce) must beSome(true)
    }
  }
}
