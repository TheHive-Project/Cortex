package org.thp.cortex.services.auth.oauth2

import java.util.UUID
import javax.inject.{Inject, Singleton}
import org.elastic4play.services.{AuthContext, AuthSrv}
import org.elastic4play.{AuthenticationError, BadRequestError, NotFoundError}
import org.thp.cortex.services.UserSrv
import org.thp.cortex.services.mappers.UserMapper
import play.api.http.Status.{FOUND, OK}
import play.api.libs.json.JsObject
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{RequestHeader, Result, Results}
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
final class OAuth2Srv(
    ws: WSClient,
    userSrv: UserSrv,
    ssoMapper: UserMapper,
    oauth2Config: Option[OAuth2Config]
)(implicit ec: ExecutionContext)
    extends AuthSrv {

  @Inject() def this(ws: WSClient, ssoMapper: UserMapper, userSrv: UserSrv, configuration: Configuration, ec: ExecutionContext) = {
    this(ws, userSrv, ssoMapper, OAuth2Config(configuration))(ec)
    // warn if configured without PKCE as a public client (empty secret)
    val isOAuth2Enabled = configuration.getDeprecated[Option[Seq[String]]]("auth.provider", "auth.type").exists(_.contains(name))
    if (isOAuth2Enabled) warnIfPublicClientWithoutPkce()
  }

  override val name: String = "oauth2"
  private val logger        = Logger(getClass)

  /** A public client (empty client secret) without PKCE: nothing protects the authorization code exchange
    */
  private def warnIfPublicClientWithoutPkce(): Unit =
    oauth2Config.filter(config => config.clientSecret.isEmpty && !config.usePkce).foreach { _ =>
      logger.warn(
        "OAuth2 client has no client secret (public client) and PKCE is disabled: the authorization code exchange is not protected. " +
          "This configuration is deprecated and will be rejected in a future version. " +
          "Enable PKCE (auth.oauth2.pkce = true), or set the client secret (auth.oauth2.clientSecret)."
      )
    }

  override def authenticate()(implicit request: RequestHeader): Future[Either[Result, AuthContext]] =
    withOAuth2Config { oauth2Config =>
      if (!isCallbackFromAuthorizationServer(request)) {
        logger.debug("Neither authorization code nor error in request, redirect to authorizationUrl")
        Future.successful(Left(authRedirect(oauth2Config)))
      } else
        (for {
          token       <- getToken(oauth2Config, request)
          userData    <- getUserData(oauth2Config, token)
          authContext <- authenticate(oauth2Config, request, userData)
        } yield Right(authContext)).recoverWith {
          case error => Future.failed(AuthenticationError(s"OAuth2 authentication failure: ${error.getMessage}"))
        }
    }

  private def withOAuth2Config[A](body: OAuth2Config => Future[A]): Future[A] =
    oauth2Config.fold[Future[A]](Future.failed(AuthenticationError("OAuth2 not configured properly")))(body)

  private def isCallbackFromAuthorizationServer(request: RequestHeader): Boolean =
    request.queryString.contains("code") || request.queryString.contains("error")

  /** Redirect the user to the authorization server, keeping in session what is needed when the user comes back.
    * With PKCE, the challenge of a new code verifier is sent, and the code verifier is kept for the token request.
    */
  private def authRedirect(oauth2Config: OAuth2Config): Result = {
    val state        = UUID.randomUUID().toString
    val codeVerifier = if (oauth2Config.usePkce) Some(Pkce.generateCodeVerifier()) else None
    val pkceParameters = codeVerifier.toSeq.flatMap { verifier =>
      Seq("code_challenge" -> Seq(Pkce.computeCodeChallenge(verifier)), "code_challenge_method" -> Seq("S256"))
    }
    val queryStringParams = Map[String, Seq[String]](
      "scope"         -> Seq(oauth2Config.scope.mkString(" ")),
      "response_type" -> Seq(oauth2Config.responseType),
      "redirect_uri"  -> Seq(oauth2Config.redirectUri),
      "client_id"     -> Seq(oauth2Config.clientId),
      "state"         -> Seq(state)
    ) ++ pkceParameters

    logger.debug(s"Redirecting to ${oauth2Config.authorizationUrl} (PKCE: ${oauth2Config.usePkce})")
    Results
      .Redirect(oauth2Config.authorizationUrl, queryStringParams, status = FOUND)
      .withSession(OAuth2Srv.LoginFlowSession(state, codeVerifier).toSession: _*)
  }

  /** Access token from the authorization server callback: its state must be the one kept in session (CSRF protection),
    * and its authorization code is exchanged for an access token, with the PKCE code verifier kept in session when PKCE is enabled
    */
  private def getToken(oauth2Config: OAuth2Config, request: RequestHeader): Future[String] =
    for {
      state        <- checkState(request)
      code         <- authorizationCode(request)
      codeVerifier <- if (oauth2Config.usePkce) getCodeVerifierFromSession(request).map(Some(_)) else Future.successful(None)
      _ = logger.debug(s"Attempting to retrieve OAuth2 token from ${oauth2Config.tokenUrl}")
      token <- getAuthTokenFromCode(oauth2Config, code, state, codeVerifier)
    } yield token

  /** The state sent back by the authorization server must be the one kept in session (CSRF protection)
    */
  private def checkState(request: RequestHeader): Future[String] = {
    val sessionState  = request.session.get(OAuth2Srv.StateSessionKey)
    val callbackState = request.queryString.get("state").flatMap(_.headOption)
    (sessionState, callbackState) match {
      case (Some(session), Some(callback)) if session == callback => Future.successful(session)
      case _                                                      => Future.failed(BadRequestError("OAuth2 states mismatch"))
    }
  }

  /** Authorization code sent back by the authorization server, or the error it sent back instead (RFC 6749 section 4.1.2.1)
    */
  private def authorizationCode(request: RequestHeader): Future[String] = {
    def parameter(name: String): Option[String] = request.queryString.get(name).flatMap(_.headOption)
    parameter("code") match {
      case Some(code) => Future.successful(code)
      case None       =>
        // error and error_description come from the query string: control characters removed, so they can't forge log lines
        val error = (parameter("error").toSeq ++ parameter("error_description")).mkString(" ").filterNot(_.isControl)
        Future.failed(AuthenticationError(s"OAuth2 authorization rejected by server: $error"))
    }
  }

  /** PKCE code verifier kept in session by the authorization redirect
    */
  private def getCodeVerifierFromSession(request: RequestHeader): Future[String] =
    request.session.get(OAuth2Srv.CodeVerifierSessionKey) match {
      case Some(codeVerifier) => Future.successful(codeVerifier)
      case None               => Future.failed(AuthenticationError("PKCE code verifier missing from session"))
    }

  /** Querying the OAuth2 server for a token
    * @param code the previously obtained code
    * @param codeVerifier the PKCE code verifier, None when PKCE is disabled
    * @return
    */
  private def getAuthTokenFromCode(oauth2Config: OAuth2Config, code: String, state: String, codeVerifier: Option[String]): Future[String] = {
    logger.trace(s"Request to ${oauth2Config.tokenUrl} for client ${oauth2Config.clientId} (PKCE: ${codeVerifier.isDefined})")
    // An empty client secret means a public client (relying on PKCE): it must not present any client secret
    val clientSecretParameters = Some(oauth2Config.clientSecret).filter(_.nonEmpty).map("client_secret" -> _)

    val pkceParameters = codeVerifier.map("code_verifier" -> _)
    ws.url(oauth2Config.tokenUrl)
      .withHttpHeaders("Accept" -> "application/json")
      .post(
        Map(
          "code"         -> code,
          "grant_type"   -> oauth2Config.grantType,
          "redirect_uri" -> oauth2Config.redirectUri,
          "client_id"    -> oauth2Config.clientId,
          "state"        -> state
        ) ++ clientSecretParameters ++ pkceParameters
      )
      .transform {
        case Success(r) if r.status == OK => Success((r.json \ "access_token").asOpt[String].getOrElse(""))
        case Failure(error)               => Failure(AuthenticationError(s"OAuth2 token verification failure ${error.getMessage}"))
        case Success(response) =>
          Failure(AuthenticationError(s"OAuth2/token request rejected by server (${response.status} ${response.statusText})${oauth2Error(response)}"))
      }
  }

  /** OAuth2 error of a token endpoint response (RFC 6749 section 5.2), empty if the response is not an OAuth2 error
    */
  private def oauth2Error(response: WSResponse): String = {
    val json        = Try(response.json).toOption
    val error       = json.flatMap(json => (json \ "error").asOpt[String])
    val description = json.flatMap(json => (json \ "error_description").asOpt[String])
    error match {
      case Some(error) => (s": $error" +: description.toSeq).mkString(" ")
      case None        => ""
    }
  }

  /** Client query for user data with OAuth2 token
    * @param token the token
    * @return
    */
  private def getUserData(oauth2Config: OAuth2Config, token: String): Future[JsObject] = {
    logger.trace(s"Request to ${oauth2Config.userUrl}")
    ws.url(oauth2Config.userUrl)
      .addHttpHeaders("Authorization" -> s"${oauth2Config.authorizationHeader} $token")
      .get()
      .transform {
        case Success(r) if r.status == OK => Success(r.json.as[JsObject])
        case Failure(error)               => Failure(AuthenticationError(s"OAuth2 user data fetch failure ${error.getMessage}"))
        case Success(r)                   => Failure(AuthenticationError(s"OAuth2/userinfo unexpected response from server (${r.status} ${r.statusText})"))
      }
  }

  private def authenticate(oauth2Config: OAuth2Config, request: RequestHeader, userData: JsObject): Future[AuthContext] =
    for {
      userFields <- ssoMapper.getUserFields(userData)
      login      <- userFields.getString("login").fold(Future.failed[String](AuthenticationError("")))(Future.successful)
      user <- userSrv
        .get(login)
        .flatMap {
          case u if oauth2Config.autoupdate =>
            logger.debug(s"Updating OAuth/OIDC user")
            userSrv.inInitAuthContext { implicit authContext =>
              // Only update name and roles, not login (can't change it)
              userSrv
                .update(u, userFields.unset("login"))

            }
          case u => Future.successful(u)
        }
        .recoverWith {
          case _: NotFoundError if oauth2Config.autocreate =>
            logger.debug(s"Creating OAuth/OIDC user")
            userSrv.inInitAuthContext { implicit authContext =>
              userSrv.create(userFields.set("login", userFields.getString("login").get.toLowerCase))
            }
        }
      authContext <- userSrv.getFromUser(request, user, name)
    } yield authContext
}

object OAuth2Srv {

  /** Session key holding the state between the authorization redirect and the authorization server callback */
  private[oauth2] val StateSessionKey = "state"

  /** Session key holding the PKCE code verifier between the authorization redirect and the token request */
  private[oauth2] val CodeVerifierSessionKey = "code_verifier"

  /** Session keys only needed during the OAuth2 login flow: to be removed from session once the user is logged in */
  val FlowSessionKeys: Set[String] = Set(StateSessionKey, CodeVerifierSessionKey)

  /** What the OAuth2 login flow keeps in session between the redirection to the authorization server and its callback
    * @param state        state sent to the authorization server, to be checked on callback (CSRF protection)
    * @param codeVerifier PKCE code verifier, to be sent with the token request (None when PKCE is disabled)
    */
  final private[oauth2] case class LoginFlowSession(state: String, codeVerifier: Option[String]) {
    def toSession: Seq[(String, String)] = (StateSessionKey -> state) +: codeVerifier.map(CodeVerifierSessionKey -> _).toSeq
  }
}
