package org.thp.cortex.controllers

import org.elastic4play.controllers.{Authenticated, FieldsBodyParser, Renderer}
import org.elastic4play.database.DBIndex
import org.elastic4play.services.{AuthContext, AuthSrv, Role}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.thp.cortex.models.{User, UserModel}
import org.thp.cortex.services.UserSrv
import play.api.Configuration
import play.api.http.HttpVerbs.GET
import play.api.libs.json.Json
import play.api.mvc.{BodyParsers, RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout, stubControllerComponents}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/** Test helpers for AuthenticationCtrl
  */
private[controllers] object AuthenticationCtrlFixtures {

  private val oauth2AuthContext: AuthContext = new AuthContext {
    override def userId: String     = "user"
    override def userName: String   = "User"
    override def requestId: String  = "request-id"
    override def roles: Seq[Role]   = Nil
    override def authMethod: String = "oauth2"
  }

  /** An authentication controller whose SSO provider successfully authenticates an active user */
  def createControllerWithSuccessfulSso(): AuthenticationCtrl = {
    val authSrv = mock(classOf[AuthSrv])
    when(authSrv.authenticate()(any[RequestHeader])).thenReturn(Future.successful(Right(oauth2AuthContext)))
    val userSrv = mock(classOf[UserSrv])
    val activeUser = new User(
      new UserModel,
      Json.obj("_id" -> "user", "_routing" -> "user", "_seqNo" -> 0L, "_primaryTerm" -> 1L, "status" -> "Ok")
    )
    when(userSrv.get("user")).thenReturn(Future.successful(activeUser))
    val dbIndex = mock(classOf[DBIndex])
    when(dbIndex.getIndexStatus).thenReturn(Future.successful(true))
    val authenticated = new Authenticated(
      maxSessionInactivity = 1.hour,
      sessionWarning = 5.minutes,
      sessionUsername = "username",
      authHeaderName = None,
      authBySessionCookie = true,
      authByKey = true,
      authByBasicAuth = false,
      authByInitialUser = false,
      authByHeader = false,
      userSrv = userSrv,
      authSrv = authSrv,
      defaultParser = mock(classOf[BodyParsers.Default]),
      ec = global
    )
    new AuthenticationCtrl(
      Configuration("play.http.context" -> "/"),
      authSrv,
      userSrv,
      authenticated,
      dbIndex,
      mock(classOf[Renderer]),
      stubControllerComponents(),
      mock(classOf[FieldsBodyParser]),
      global
    )
  }

  /** The authorization server redirects the user back to Cortex with an authorization code, within an ongoing OAuth2 flow */
  def completeSsoLogin(controller: AuthenticationCtrl, session: Seq[(String, String)]): Result =
    await(controller.ssoLogin(FakeRequest(GET, "/api/ssoLogin?code=authorization-code&state=the-state").withSession(session: _*)))
}
