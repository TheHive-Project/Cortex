package org.thp.cortex.controllers

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.thp.cortex.controllers.AuthenticationCtrlFixtures._
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class AuthenticationCtrlSpec extends PlaySpecification {

  "AuthenticationCtrl.ssoLogin" should {

    "remove the OAuth2 flow data from session once the user is logged in" in {
      // GIVEN - a successful SSO authentication, within an OAuth2 flow whose state and code verifier are in session
      val controller = createControllerWithSuccessfulSso()
      val session    = Seq("state" -> "the-state", "code_verifier" -> "the-code-verifier")

      // WHEN - the user completes the SSO login
      val result = completeSsoLogin(controller, session)

      // THEN - the user is logged in, and the state and code verifier are no longer in session
      val newSession = result.newSession.map(_.data).getOrElse(Map.empty)
      newSession.get("username") must beSome("user")
      newSession.keySet must not(contain("state"))
      newSession.keySet must not(contain("code_verifier"))
    }
  }
}
