package org.thp.cortex.services

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.{FakeRequest, PlaySpecification}

@RunWith(classOf[JUnitRunner])
class LoggedUriSpec extends PlaySpecification {

  "LoggedUri" should {

    "not log the query string of the SSO callback, which holds the OAuth2 authorization code and state" in {
      // GIVEN - the authorization server redirects the user back to Cortex with a code and a state
      val request = FakeRequest(GET, "/api/ssoLogin?code=authorization-code&state=the-state")

      // WHEN - the request URI is written in logs
      val loggedUri = LoggedUri(request)

      // THEN - only its path is written
      loggedUri must_== "/api/ssoLogin"
    }

    "not log the query string of the SSO callback when Cortex is served under an HTTP context" in {
      // GIVEN - Cortex served under /cortex, and the authorization server redirecting the user back with a code and a state
      val request = FakeRequest(GET, "/cortex/api/ssoLogin?code=authorization-code&state=the-state")

      // WHEN - the request URI is written in logs
      val loggedUri = LoggedUri(request)

      // THEN - only its path is written
      loggedUri must_== "/cortex/api/ssoLogin"
    }

    "log the query string of the other requests" in {
      // GIVEN - a search request with its query parameters
      val request = FakeRequest(GET, "/api/job?range=all&sort=-createdAt")

      // WHEN - the request URI is written in logs
      val loggedUri = LoggedUri(request)

      // THEN - the whole URI is written, query string included
      loggedUri must_== "/api/job?range=all&sort=-createdAt"
    }
  }
}
