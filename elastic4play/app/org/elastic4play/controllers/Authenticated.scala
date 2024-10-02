package org.elastic4play.controllers

import java.io.ByteArrayInputStream
import java.util.Date
import javax.inject.{Inject, Singleton}
import javax.naming.ldap.LdapName

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

import play.api.{Configuration, Logger}
import play.api.http.HeaderNames
import play.api.mvc._

import org.bouncycastle.asn1._

import org.elastic4play.{AuthenticationError, AuthorizationError}
import org.elastic4play.services.{AuthContext, AuthSrv, Role, UserSrv}
import org.elastic4play.utils.Instance
import java.util.{List => JList}

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
    certificateField: Option[String],
    authHeaderName: Option[String],
    authBySessionCookie: Boolean,
    authByKey: Boolean,
    authByBasicAuth: Boolean,
    authByInitialUser: Boolean,
    authByPki: Boolean,
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
      configuration
        .getOptional[String]("auth.pki.certificateField")
        .map(_.toLowerCase)
        .map {
          case "userprincipalname" => "upn"
          case f                   => f
        },
      configuration.getOptional[String]("auth.header.name"),
      configuration.getOptional[Boolean]("auth.method.session").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.key").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.basic").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.init").getOrElse(true),
      configuration.getOptional[Boolean]("auth.method.pki").getOrElse(true),
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

  private def asn1String(obj: ASN1Primitive): String = obj match {
    case ds: DERUTF8String    => DERUTF8String.getInstance(ds).getString
    case to: ASN1TaggedObject => asn1String(ASN1TaggedObject.getInstance(to).getObject)
    case os: ASN1OctetString  => new String(os.getOctets)
    case as: ASN1String       => as.getString
  }

  private object CertificateSAN {

    def unapply(l: JList[_]): Option[(String, String)] = {
      val typeValue = for {
        t <- Option(l.get(0))
        v <- Option(l.get(1))
      } yield t -> v
      typeValue
        .collect { case (t: Integer, v) => t.toInt -> v }
        .collect {
          case (0, value: Array[Byte]) =>
            val asn1     = new ASN1InputStream(new ByteArrayInputStream(value)).readObject()
            val asn1Seq  = ASN1Sequence.getInstance(asn1)
            val id       = ASN1ObjectIdentifier.getInstance(asn1Seq.getObjectAt(0)).getId
            val valueStr = asn1String(asn1Seq.getObjectAt(1).toASN1Primitive)

            id match {
              case "1.3.6.1.4.1.311.20.2.3" => "upn" -> valueStr
              // Add other object id
              case other => other -> valueStr
            }
          case (1, value: String) => "rfc822Name"                -> value
          case (2, value: String) => "dNSName"                   -> value
          case (3, value: String) => "x400Address"               -> value
          case (4, value: String) => "directoryName"             -> value
          case (5, value: String) => "ediPartyName"              -> value
          case (6, value: String) => "uniformResourceIdentifier" -> value
          case (7, value: String) => "iPAddress"                 -> value
          case (8, value: String) => "registeredID"              -> value
        }
    }
  }

  def getFromClientCertificate(request: RequestHeader): Future[AuthContext] =
    certificateField
      .fold[Future[AuthContext]](Future.failed(AuthenticationError("Certificate authentication is not configured"))) { cf =>
        logger.debug(s"Client certificate is : ${request.clientCertificateChain.toList.flatten.map(_.getSubjectX500Principal.getName).mkString(";")}")
        request
          .clientCertificateChain
          .flatMap(_.headOption)
          .flatMap { cert =>
            val dn       = cert.getSubjectX500Principal.getName
            val ldapName = new LdapName(dn)
            val rdns     = ldapName.getRdns.asScala
            logger.debug(s"Client certificate subject is ${rdns.map(x => x.getType + "=" + x.getValue.toString).mkString(",")}")
            rdns
              .collectFirst {
                case rdn if rdn.getType.toLowerCase == cf =>
                  logger.debug(s"Found user id ${rdn.getValue} in dn:$cf")
                  userSrv.getFromId(request, rdn.getValue.toString.toLowerCase, "pki")
              }
              .orElse {
                logger.debug(s"Field $cf not found in certificate subject")
                for {
                  san <- Option(cert.getSubjectAlternativeNames)
                  _ = logger.debug(s"Subject alternative name is ${san.asScala.mkString(",")}")
                  fieldValue <- san.asScala.collectFirst {
                    case CertificateSAN(name, value) if name.toLowerCase == cf =>
                      logger.debug(s"Found user id $value in san:$cf")
                      userSrv.getFromId(request, value.toLowerCase, "pki")
                  }
                } yield fieldValue
              }
          }
          .getOrElse(Future.failed(AuthenticationError("Certificate doesn't contain user information")))
      }

  def getFromHeader(request: RequestHeader): Future[AuthContext] =
    for {
      header   <- authHeaderName.fold[Future[String]](Future.failed(AuthenticationError("HTTP header is not configured")))(Future.successful)
      username <- request.headers.get(header).fold[Future[String]](Future.failed(AuthenticationError("HTTP header is not set")))(Future.successful)
      user     <- userSrv.getFromId(request, username.toLowerCase, "header")
    } yield user

  val authenticationMethods: Seq[(String, RequestHeader => Future[AuthContext])] =
    (if (authBySessionCookie) Seq("session" -> getFromSession _) else Nil) ++
      (if (authByPki) Seq("pki"             -> getFromClientCertificate _) else Nil) ++
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
