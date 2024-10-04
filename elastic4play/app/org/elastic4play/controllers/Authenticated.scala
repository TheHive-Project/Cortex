package org.elastic4play.controllers

import org.elastic4play.services.{AuthContext, AuthSrv, Role, UserSrv}
import org.elastic4play.utils.Instance
import org.elastic4play.{AuthenticationError, AuthorizationError}
import play.api.http.HeaderNames
import play.api.mvc._
import play.api.{Configuration, Logger}

import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * A request with authentication information
  */
class AuthenticatedRequest[A](val authContext: AuthContext, request: Request[A]) extends WrappedRequest[A](request) with AuthContext {
  def userId: String     = authContext.userId
  def userName: String   = authContext.userName
  def requestId: String  = Instance.getRequestId(request)
  def roles: Seq[Role]   = authContext.roles
  def authMethod: String = authContext.authMethod
}

sealed trait ExpirationStatus
case class ExpirationOk(duration: FiniteDuration)      extends ExpirationStatus
case class ExpirationWarning(duration: FiniteDuration) extends ExpirationStatus
case object ExpirationError                            extends ExpirationStatus

/**
  * Check and manager user security (authentication and authorization)
  */
@Singleton
class Authenticated(
    maxSessionInactivity: FiniteDuration,
    sessionWarning: FiniteDuration,
    sessionUsername: String,
    authHeaderName: Option[String],
    authBySessionCookie: Boolean,
    authByKey: Boolean,
    authByBasicAuth: Boolean,
    authByInitialUser: Boolean,
    authByHeader: Boolean,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    defaultParser: BodyParsers.Default,
    implicit val ec: ExecutionContext
) {

  @Inject() def this(configuration: Configuration, userSrv: UserSrv, authSrv: AuthSrv, defaultParser: BodyParsers.Default, ec: ExecutionContext) =
    this(
      configuration.getMillis("session.inactivity").millis,
      configuration.getMillis("session.warning").millis,
      configuration.getOptional[String]("session.username").getOrElse("username"),
      configuration.getOptional[String]("auth.header.name"),
      configuration.getOptional[Boolean]("auth.method.session").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.key").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.basic").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.init").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.header").getOrElse(false),
      userSrv,
      authSrv,
      defaultParser,
      ec
    )

  private[Authenticated] lazy val logger = Logger(getClass)

  private def now = (new Date).getTime

  /**
    * Insert or update session cookie containing user name and session expiration timestamp
    * Cookie is signed by Play framework (it cannot be modified by user)
    */
  def setSessingUser(result: Result, authContext: AuthContext)(implicit request: RequestHeader): Result =
    if (authContext.authMethod != "key" && authContext.authMethod != "init")
      result.addingToSession(
        sessionUsername -> authContext.userId,
        "expire"        -> (now + maxSessionInactivity.toMillis).toString,
        "authMethod"    -> authContext.authMethod
      )
    else
      result

  /**
    * Retrieve authentication information form cookie
    */
  def getFromSession(request: RequestHeader): Future[AuthContext] = {
    val authContext = for {
      userId     <- request.session.get(sessionUsername).toRight(AuthenticationError("User session not found"))
      authMethod <- request.session.get("authMethod").toRight(AuthenticationError("Authentication method not found in session"))
      _          <- if (expirationStatus(request) != ExpirationError) Right(()) else Left(AuthenticationError("User session has expired"))
      ctx = userSrv.getFromId(request, userId, authMethod)
    } yield ctx
    authContext.fold(authError => Future.failed[AuthContext](authError), identity)
  }

  def expirationStatus(request: RequestHeader): ExpirationStatus =
    request
      .session
      .get("expire")
      .flatMap { expireStr =>
        Try(expireStr.toLong).toOption
      }
      .map { expire =>
        (expire - now).millis
      }
      .map {
        case duration if duration.length < 0       => ExpirationError
        case duration if duration < sessionWarning => ExpirationWarning(duration)
        case duration                              => ExpirationOk(duration)
      }
      .getOrElse(ExpirationError)

  /**
    * Retrieve authentication information from API key
    */
  def getFromApiKey(request: RequestHeader): Future[AuthContext] =
    for {
      auth <- request
        .headers
        .get(HeaderNames.AUTHORIZATION)
        .fold(Future.failed[String](AuthenticationError("Authentication header not found")))(Future.successful)
      _ <- if (!auth.startsWith("Bearer ")) Future.failed(AuthenticationError("Only bearer authentication is supported")) else Future.successful(())
      key = auth.substring(7)
      authContext <- authSrv.authenticate(key)(request)
    } yield authContext

  def getFromBasicAuth(request: RequestHeader): Future[AuthContext] =
    for {
      auth <- request
        .headers
        .get(HeaderNames.AUTHORIZATION)
        .fold(Future.failed[String](AuthenticationError("Authentication header not found")))(Future.successful)
      _ <- if (!auth.startsWith("Basic ")) Future.failed(AuthenticationError("Only basic authentication is supported")) else Future.successful(())
      authWithoutBasic = auth.substring(6)
      decodedAuth      = new String(java.util.Base64.getDecoder.decode(authWithoutBasic), "UTF-8")
      authContext <- decodedAuth.split(":") match {
        case Array(username, password) => authSrv.authenticate(username, password)(request)
        case _                         => Future.failed(AuthenticationError("Can't decode authentication header"))
      }
    } yield authContext

  def getFromHeader(request: RequestHeader): Future[AuthContext] =
    for {
      header   <- authHeaderName.fold[Future[String]](Future.failed(AuthenticationError("HTTP header is not configured")))(Future.successful)
      username <- request.headers.get(header).fold[Future[String]](Future.failed(AuthenticationError("HTTP header is not set")))(Future.successful)
      user     <- userSrv.getFromId(request, username.toLowerCase, "header")
    } yield user

  val authenticationMethods: Seq[(String, RequestHeader => Future[AuthContext])] =
    (if (authBySessionCookie) Seq("session" -> getFromSession _) else Nil) ++
      (if (authByKey) Seq("key"             -> getFromApiKey _) else Nil) ++
      (if (authByBasicAuth) Seq("basic"     -> getFromBasicAuth _) else Nil) ++
      (if (authByInitialUser) Seq("init"    -> userSrv.getInitialUser _) else Nil) ++
      (if (authByHeader) Seq("header"       -> getFromHeader _) else Nil)

  def getContext(request: RequestHeader): Future[AuthContext] =
    authenticationMethods
      .foldLeft[Future[Either[Seq[(String, Throwable)], AuthContext]]](Future.successful(Left(Nil))) {
        case (acc, (authMethodName, authMethod)) =>
          acc.flatMap {
            case authContext if authContext.isRight => Future.successful(authContext)
            case Left(errors) =>
              authMethod(request)
                .map(authContext => Right(authContext))
                .recover { case error => Left(errors :+ (authMethodName -> error)) }
          }
      }
      .flatMap {
        case Right(authContext) => Future.successful(authContext)
        case Left(errors) =>
          val errorDetails = errors
            .map { case (authMethodName, error) => s"\t$authMethodName: ${error.getClass.getSimpleName} ${error.getMessage}" }
            .mkString("\n")
          logger.error(s"Authentication failure:\n$errorDetails")
          Future.failed(AuthenticationError("Authentication failure"))
      }

  /**
    * Create an action for authenticated controller
    * If user has sufficient right (have required role) action is executed
    * otherwise, action returns a not authorized error
    */
  def apply(requiredRole: Role*): ActionBuilder[AuthenticatedRequest, AnyContent] =
    new ActionBuilder[AuthenticatedRequest, AnyContent] {
      val executionContext: ExecutionContext = ec

      def parser: BodyParser[AnyContent] = defaultParser

      def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
        getContext(request).flatMap { authContext =>
          if (requiredRole.isEmpty || requiredRole.toSet.intersect(authContext.roles.toSet).nonEmpty)
            block(new AuthenticatedRequest(authContext, request))
              .map(result => setSessingUser(result, authContext)(request))
          else
            Future.failed(AuthorizationError(s"Insufficient rights to perform this action"))
        }
    }
}
