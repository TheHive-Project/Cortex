package org.thp.cortex.services.auth.oauth2

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import org.elastic4play.NotFoundError
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{AuthContext, Role, User => EUser}
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{mock, when}
import org.specs2.matcher.{Expectable, MatchResult, Matcher}
import org.thp.cortex.models.{User, UserModel}
import org.thp.cortex.services.UserSrv
import org.thp.cortex.services.mappers.UserMapper
import play.api.http.HeaderNames.{AUTHORIZATION, LOCATION}
import play.api.http.HttpVerbs.GET
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{RequestHeader, Result, Results}
import play.api.routing.sird
import play.api.routing.sird._
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test.{FakeRequest, WsTestClient}
import play.core.parsers.FormUrlEncodedParser
import play.core.server.Server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

/** Test helpers for OAuth2Srv
  */
private[oauth2] object OAuth2Fixtures {

  /** OAuth2 configuration of a client registered at https://idp.example */
  private def createOAuth2Config(
      tokenUrl: String = "https://idp.example/token",
      userUrl: String = "https://idp.example/userinfo",
      usePkce: Boolean = false,
      clientSecret: String = "client-secret"
  ): OAuth2Config =
    OAuth2Config(
      clientId = "client-id",
      clientSecret = clientSecret,
      redirectUri = "https://cortex.example/api/ssoLogin",
      responseType = "code",
      grantType = "authorization_code",
      authorizationUrl = "https://idp.example/authorize",
      tokenUrl = tokenUrl,
      userUrl = userUrl,
      scope = Seq("openid", "profile"),
      authorizationHeader = "Bearer",
      autoupdate = false,
      autocreate = false,
      usePkce = usePkce
    )

  /** Login of the user known by the fake authorization server, and existing in Cortex */
  val existingUserLogin = "user"

  /** Maps the user info of the fake authorization server (`{"sub": login}`) to the fields of a Cortex user */
  private object FakeUserMapper extends UserMapper {
    override val name: String = "fake"
    override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] =
      Future.successful(Fields.empty.set("login", (jsValue \ "sub").as[String]))
  }

  /** Cortex users: only existingUserLogin exists, and it is active */
  private def createUserSrv(): UserSrv = {
    val userSrv = mock(classOf[UserSrv])
    val user = new User(
      new UserModel,
      Json.obj("_id" -> existingUserLogin, "_routing" -> existingUserLogin, "_seqNo" -> 0L, "_primaryTerm" -> 1L, "status" -> "Ok")
    )
    when(userSrv.get(anyString())).thenReturn(Future.failed(NotFoundError("user not found")))
    when(userSrv.get(existingUserLogin)).thenReturn(Future.successful(user))
    when(userSrv.getFromUser(any[RequestHeader], any[EUser], anyString())).thenReturn(Future.successful(authContextOf(existingUserLogin)))
    userSrv
  }

  private def authContextOf(login: String): AuthContext =
    new AuthContext {
      final override def userId: String     = login
      final override def userName: String   = login
      final override def requestId: String  = "request-id"
      final override def roles: Seq[Role]   = Nil
      final override def authMethod: String = "oauth2"
    }

  /** An OAuth2 service whose authorization server is not reachable (none of its endpoints can be called) */
  def createOAuth2Service(usePkce: Boolean = false): OAuth2Srv =
    new OAuth2Srv(
      ws = mock(classOf[WSClient]),
      userSrv = createUserSrv(),
      ssoMapper = FakeUserMapper,
      oauth2Config = Some(createOAuth2Config(usePkce = usePkce))
    )(global)

  /** The user starts to log in (no code nor state in request): return the redirection to the authorization server */
  def startLogIn(srv: OAuth2Srv): Result =
    await(srv.authenticate()(FakeRequest())).swap.getOrElse(throw new IllegalStateException("expected a redirection"))

  /** The user denies the authorization: the authorization server redirects back with an error (RFC 6749 section 4.1.2.1) */
  def denyAuthorization(srv: OAuth2Srv, session: Seq[(String, String)]): Try[Either[Result, AuthContext]] = {
    val callback = FakeRequest(GET, "/api/ssoLogin?error=access_denied&error_description=User%20denied%20access&state=the-state")
    Try(await(srv.authenticate()(callback.withSession(session: _*))))
  }

  /** Parameters of the authorization request the browser sends to the authorization server when following the redirection */
  def readAuthorizationRequest(redirection: Result): Map[String, Seq[String]] =
    FormUrlEncodedParser.parse(new URI(readLocation(redirection)).getRawQuery)

  /** Authorization endpoint the browser is redirected to (without query string) */
  def readAuthorizationEndpoint(redirection: Result): String =
    readLocation(redirection).takeWhile(_ != '?')

  /** State kept in Cortex session by the redirection */
  def readStateFromSession(redirection: Result): Option[String] =
    redirection.newSession.flatMap(_.get("state"))

  /** Whole Cortex session set by the redirection, as the browser sends it back on the callback */
  def readSession(redirection: Result): Seq[(String, String)] =
    redirection.newSession.map(_.data.toSeq).getOrElse(Nil)

  /** PKCE code verifier kept in Cortex session by the redirection */
  def readCodeVerifierFromSession(redirection: Result): Option[String] =
    redirection.newSession.flatMap(_.get("code_verifier"))

  private def readLocation(redirection: Result): String =
    redirection.header.headers.getOrElse(LOCATION, throw new IllegalStateException("Location header missing"))

  /** Result of an authorization code exchange
    * @param tokenRequest          form received by the token endpoint, None if it has not been called
    * @param userInfoAuthorization Authorization header received by the user info endpoint, None if it has not been called
    * @param outcome               outcome of the authentication
    */
  final case class CodeExchange(
      tokenRequest: Option[Map[String, Seq[String]]],
      userInfoAuthorization: Option[String],
      outcome: Try[Either[Result, AuthContext]]
  ) {

    /** What the token endpoint received, or why it has not been called: used in failure messages */
    def describeTokenRequest: String =
      tokenRequest.fold(s"the token endpoint has not been called, authentication outcome: $outcome")(form => s"the token endpoint received $form")
  }

  private def formatParameters(parameters: Seq[(String, String)]): String =
    parameters.map { case (name, value) => s"$name=$value" }.mkString(", ")

  /** The token endpoint has been called and has received all these parameters */
  def sendToTokenEndpoint(parameters: (String, String)*): Matcher[CodeExchange] =
    new Matcher[CodeExchange] {
      final def apply[S <: CodeExchange](exchange: Expectable[S]): MatchResult[S] = {
        val received = exchange.value.tokenRequest.getOrElse(Map.empty)
        val missing  = parameters.filterNot { case (name, value) => received.get(name).contains(Seq(value)) }
        result(
          exchange.value.tokenRequest.isDefined && missing.isEmpty,
          s"the token endpoint received ${formatParameters(parameters)}",
          s"the token endpoint did not receive ${formatParameters(missing)}: ${exchange.value.describeTokenRequest}",
          exchange
        )
      }
    }

  /** The user info endpoint has been called with this Authorization header */
  def requestUserInfoWith(authorization: String): Matcher[CodeExchange] =
    new Matcher[CodeExchange] {
      final def apply[S <: CodeExchange](exchange: Expectable[S]): MatchResult[S] =
        result(
          exchange.value.userInfoAuthorization.contains(authorization),
          s"the user info endpoint received Authorization: $authorization",
          s"the user info endpoint did not receive Authorization: $authorization (received: ${exchange.value.userInfoAuthorization}, ${exchange.value.describeTokenRequest})",
          exchange
        )
    }

  /** Token endpoint response with an access token (RFC 6749 section 5.1) */
  val validTokenResponse: Result = Results.Ok(Json.obj("access_token" -> "access-token", "token_type" -> "Bearer"))

  /** Token endpoint error response (RFC 6749 section 5.2) */
  val tokenErrorResponse: Result =
    Results.BadRequest(Json.obj("error" -> "invalid_grant", "error_description" -> "PKCE verification failed"))

  /** The authorization server redirects back with an authorization code (code and state in request):
    * exchange it against a fake authorization server
    */
  def exchangeAuthorizationCode(
      session: Seq[(String, String)],
      usePkce: Boolean = false,
      clientSecret: String = "client-secret",
      tokenResponse: Result = validTokenResponse,
      callbackState: String = "the-state"
  ): CodeExchange = {
    val tokenRequest          = new AtomicReference[Option[Map[String, Seq[String]]]](None)
    val userInfoAuthorization = new AtomicReference[Option[String]](None)
    val outcome = Server.withRouterFromComponents() { components =>
      {
        case sird.POST(p"/token") =>
          components.defaultActionBuilder { request =>
            tokenRequest.set(Some(request.body.asFormUrlEncoded.getOrElse(Map.empty)))
            tokenResponse
          }
        case sird.GET(p"/userinfo") =>
          components.defaultActionBuilder { request =>
            userInfoAuthorization.set(Some(request.headers.get(AUTHORIZATION).getOrElse("")))
            Results.Ok(Json.obj("sub" -> existingUserLogin))
          }
      }
    } { implicit port =>
      WsTestClient.withClient { ws =>
        val srv = new OAuth2Srv(
          ws,
          createUserSrv(),
          FakeUserMapper,
          Some(
            createOAuth2Config(
              tokenUrl = s"http://localhost:$port/token",
              userUrl = s"http://localhost:$port/userinfo",
              usePkce = usePkce,
              clientSecret = clientSecret
            )
          )
        )(global)
        val request = FakeRequest(GET, s"/api/ssoLogin?code=authorization-code&state=$callbackState").withSession(session: _*)
        Try(await(srv.authenticate()(request)))
      }
    }
    CodeExchange(tokenRequest.get, userInfoAuthorization.get, outcome)
  }
}
