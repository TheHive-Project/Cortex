package org.thp.cortex.services.auth.oauth2

import org.elastic4play.AuthenticationError
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.thp.cortex.services.auth.oauth2.OAuth2Fixtures._
import play.api.test.PlaySpecification

/** End-to-end tests of the OAuth2 flow (session, network calls, steps sequence), against a fake authorization server
  */
@RunWith(classOf[JUnitRunner])
class OAuth2SrvSpec extends PlaySpecification {

  "OAuth2Srv.authenticate" should {

    "redirect to the authorization endpoint with a state kept in session" in {
      // GIVEN - an OAuth2 service
      val srv = createOAuth2Service()

      // WHEN - the user starts to log in
      val redirection = startLogIn(srv)

      // THEN - the authorization server receives the client, the scope and a state, which is kept in session
      val authorizationRequest = readAuthorizationRequest(redirection)
      val state                = readStateFromSession(redirection)
      readAuthorizationEndpoint(redirection) must_== "https://idp.example/authorize"
      authorizationRequest.get("response_type") must beSome(Seq("code"))
      authorizationRequest.get("client_id") must beSome(Seq("client-id"))
      authorizationRequest.get("redirect_uri") must beSome(Seq("https://cortex.example/api/ssoLogin"))
      authorizationRequest.get("scope") must beSome(Seq("openid profile"))
      state must beSome[String]
      authorizationRequest.get("state") must beSome(Seq(state.get))
    }
  }

  "OAuth2Srv.authenticate with an authorization code" should {

    "send the code, the redirect URI and the client credentials to the token endpoint" in {
      // GIVEN - a session holding the state
      val session = Seq("state" -> "the-state")

      // WHEN - the authorization code is exchanged
      val exchange = exchangeAuthorizationCode(session)

      // THEN - the token endpoint receives an authorization code grant, authenticated with the client secret
      exchange must sendToTokenEndpoint(
        "grant_type"    -> "authorization_code",
        "code"          -> "authorization-code",
        "redirect_uri"  -> "https://cortex.example/api/ssoLogin",
        "client_id"     -> "client-id",
        "client_secret" -> "client-secret"
      )
    }

    "fail without calling the token endpoint when the state does not match the one in session" in {
      // GIVEN - a session holding another state than the one sent back by the authorization server
      val session = Seq("state" -> "another-state")

      // WHEN - the authorization code is exchanged
      val exchange = exchangeAuthorizationCode(session)

      // THEN - the token endpoint is not called and the authentication fails
      exchange.tokenRequest must beNone
      exchange.outcome must beFailedTry.withThrowable[AuthenticationError](".*state.*")
    }

    "request the user info with the access token returned by the token endpoint" in {
      // GIVEN - a session holding the state, and a token endpoint returning an access token
      val session = Seq("state" -> "the-state")

      // WHEN - the authorization code is exchanged
      val exchange = exchangeAuthorizationCode(session)

      // THEN - the user info endpoint is called with the access token
      exchange must requestUserInfoWith("Bearer access-token")
    }

    "log in the user returned by the user info endpoint" in {
      // GIVEN - a session holding the state, and an authorization server knowing a user who exists in Cortex
      val session = Seq("state" -> "the-state")

      // WHEN - the authorization code is exchanged
      val exchange = exchangeAuthorizationCode(session)

      // THEN - the authentication succeeds for this user
      exchange.outcome.map(_.map(_.userId)) must beSuccessfulTry(Right(existingUserLogin))
    }
  }
}
