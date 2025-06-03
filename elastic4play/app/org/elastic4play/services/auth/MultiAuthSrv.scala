package org.elastic4play.services.auth

import javax.inject.{Inject, Singleton}
import org.elastic4play.AuthenticationError
import org.elastic4play.services.AuthCapability.Type
import org.elastic4play.services.{AuthContext, AuthSrv}
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logger}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object MultiAuthSrv {
  private[MultiAuthSrv] lazy val logger = Logger(getClass)
}

@Singleton
class MultiAuthSrv(val authProviders: Seq[AuthSrv], implicit val ec: ExecutionContext) extends AuthSrv {

  @Inject() def this(configuration: Configuration, authModules: immutable.Set[AuthSrv], ec: ExecutionContext) =
    this(
      configuration
        .getDeprecated[Option[Seq[String]]]("auth.provider", "auth.type")
        .getOrElse(Nil)
        .flatMap { authType =>
          authModules
            .find(_.name == authType)
            .orElse {
              MultiAuthSrv.logger.error(s"Authentication module $authType not found")
              None
            }
        },
      ec
    )

  val name                             = "multi"
  override val capabilities: Set[Type] = authProviders.flatMap(_.capabilities).toSet

  private[auth] def forAllAuthProvider[A](body: AuthSrv => Future[A]) =
    authProviders.foldLeft(Future.failed[A](new Exception("no authentication provider found"))) { (f, a) =>
      f.recoverWith {
        case _ =>
          val r = body(a)
          r.failed.foreach(error => MultiAuthSrv.logger.debug(s"${a.name} ${error.getClass.getSimpleName} ${error.getMessage}"))
          r
      }
    }

  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
    forAllAuthProvider(_.authenticate(username, password))
      .recoverWith {
        case authError =>
          MultiAuthSrv.logger.error("Authentication failure", authError)
          Future.failed(AuthenticationError("Authentication failure"))
      }

  override def authenticate(key: String)(implicit request: RequestHeader): Future[AuthContext] =
    forAllAuthProvider(_.authenticate(key))
      .recoverWith {
        case authError =>
          MultiAuthSrv.logger.error("Authentication failure", authError)
          Future.failed(AuthenticationError("Authentication failure"))
      }

  override def authenticate()(implicit request: RequestHeader): Future[Either[Result, AuthContext]] =
    forAllAuthProvider(_.authenticate())
      .recoverWith {
        case authError =>
          MultiAuthSrv.logger.error("Authentication failure", authError)
          Future.failed(AuthenticationError("Authentication failure"))
      }

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    forAllAuthProvider(_.changePassword(username, oldPassword, newPassword))

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    forAllAuthProvider(_.setPassword(username, newPassword))

  override def renewKey(username: String)(implicit authContext: AuthContext): Future[String] =
    forAllAuthProvider(_.renewKey(username))

  override def getKey(username: String)(implicit authContext: AuthContext): Future[String] =
    forAllAuthProvider(_.getKey(username))

  override def setKey(username: String, key: String)(implicit authContext: AuthContext): Future[String] =
    forAllAuthProvider(_.setKey(username, key))

  override def removeKey(username: String)(implicit authContext: AuthContext): Future[Unit] =
    forAllAuthProvider(_.removeKey(username))
}
