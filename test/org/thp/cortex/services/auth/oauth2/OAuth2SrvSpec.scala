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

    "send a S256 code challenge matching the code verifier kept in session when PKCE is enabled" in {
      // GIVEN - an OAuth2 service with PKCE enabled
      val srv = createOAuth2Service(usePkce = true)

      // WHEN - the user starts to log in
      val redirection = startLogIn(srv)

      // THEN - a code verifier is kept in session and its S256 challenge is sent to the authorization server
      val codeVerifier         = readCodeVerifierFromSession(redirection)
      val authorizationRequest = readAuthorizationRequest(redirection)
      codeVerifier must beSome[String]
      authorizationRequest.get("code_challenge_method") must beSome(Seq("S256"))
      authorizationRequest.get("code_challenge") must beSome(Seq(Pkce.computeCodeChallenge(codeVerifier.get)))
    }

    "not send any code challenge when PKCE is disabled" in {
      // GIVEN - an OAuth2 service with PKCE disabled
      val srv = createOAuth2Service()

      // WHEN - the user starts to log in
      val redirection = startLogIn(srv)

      // THEN - no code verifier is kept in session and no challenge is sent to the authorization server
      val authorizationRequest = readAuthorizationRequest(redirection)
      readCodeVerifierFromSession(redirection) must beNone
      authorizationRequest.keySet must not(contain("code_challenge"))
      authorizationRequest.keySet must not(contain("code_challenge_method"))
    }
  }

  "OAuth2Srv.authenticate with an authorization error" should {

    "fail with the error sent back by the authorization server, instead of redirecting to it again" in {
      // GIVEN - an OAuth2 flow started by the user, whose state is in session
      val srv     = createOAuth2Service()
      val session = Seq("state" -> "the-state")

      // WHEN - the user denies the authorization
      val outcome = denyAuthorization(srv, session)

      // THEN - the authentication fails with the error sent back by the authorization server
      outcome must beFailedTry.withThrowable[AuthenticationError](".*access_denied.*User denied access.*")
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

    "not send any client secret to the token endpoint for a public client (empty client secret)" in {
      // GIVEN - a public client (empty client secret) relying on PKCE, and a session holding the state and the code verifier
      val session = Seq("state" -> "the-state", "code_verifier" -> "the-code-verifier")

      // WHEN - the authorization code is exchanged
      val exchange = exchangeAuthorizationCode(session, usePkce = true, clientSecret = "")

      // THEN - the token endpoint receives the client id and the code verifier, but no client secret
      exchange must sendToTokenEndpoint("client_id" -> "client-id", "code_verifier" -> "the-code-verifier")
      exchange.tokenRequest.flatMap(_.get("client_secret")) must beNone
    }

    "send the code verifier kept in session to the token endpoint when PKCE is enabled" in {
      // GIVEN - a session holding the state and the code verifier
      val session = Seq("state" -> "the-state", "code_verifier" -> "the-code-verifier")

      // WHEN - the authorization code is exchanged with PKCE enabled
      val exchange = exchangeAuthorizationCode(session, usePkce = true)

      // THEN - the token endpoint receives the code and the code verifier
      exchange must sendToTokenEndpoint("code" -> "authorization-code", "code_verifier" -> "the-code-verifier")
    }

    "not send any code verifier to the token endpoint when PKCE is disabled" in {
      // GIVEN - a session holding the state and a code verifier
      val session = Seq("state" -> "the-state", "code_verifier" -> "the-code-verifier")

      // WHEN - the authorization code is exchanged with PKCE disabled
      val exchange = exchangeAuthorizationCode(session)

      // THEN - the token endpoint receives the code but no code verifier
      exchange must sendToTokenEndpoint("code" -> "authorization-code")
      exchange.tokenRequest.flatMap(_.get("code_verifier")) must beNone
    }

    "fail without calling the token endpoint when PKCE is enabled and the code verifier is missing from session" in {
      // GIVEN - a session holding the state but no code verifier (e.g. expired or lost session cookie)
      val session = Seq("state" -> "the-state")

      // WHEN - the authorization code is exchanged with PKCE enabled
      val exchange = exchangeAuthorizationCode(session, usePkce = true)

      // THEN - the token endpoint is not called and the authentication fails because of the missing code verifier
      exchange.tokenRequest must beNone
      exchange.outcome must beFailedTry.withThrowable[AuthenticationError](".*code verifier.*")
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

    "fail with the OAuth2 error returned by the token endpoint, without requesting the user info" in {
      // GIVEN - a session holding the state, and a token endpoint rejecting the authorization code
      val session = Seq("state" -> "the-state")

      // WHEN - the authorization code is exchanged
      val exchange = exchangeAuthorizationCode(session, tokenResponse = tokenErrorResponse)

      // THEN - the user info endpoint is not called, and the authentication fails with the error of the token endpoint
      exchange.userInfoAuthorization must beNone
      exchange.outcome must beFailedTry.withThrowable[AuthenticationError](".*invalid_grant.*PKCE verification failed.*")
    }

    "send to the token endpoint the code verifier whose challenge was sent to the authorization server (PKCE round trip)" in {
      // GIVEN - the user started to log in with PKCE: Cortex sent a challenge and kept its session
      val redirection          = startLogIn(createOAuth2Service(usePkce = true))
      val authorizationRequest = readAuthorizationRequest(redirection)
      val session              = readSession(redirection)

      // WHEN - the authorization server redirects the user back with a code, the browser sending back the session
      val exchange = exchangeAuthorizationCode(session, usePkce = true, callbackState = readStateFromSession(redirection).get)

      // THEN - the token endpoint receives the code verifier whose challenge was sent to the authorization server
      val sentCodeVerifier = exchange.tokenRequest.flatMap(_.get("code_verifier")).flatMap(_.headOption)
      sentCodeVerifier must beSome[String]
      authorizationRequest.get("code_challenge") must beSome(Seq(Pkce.computeCodeChallenge(sentCodeVerifier.get)))
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
