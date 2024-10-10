package org.elastic4play.services

import scala.concurrent.Future
import play.api.libs.json.JsObject
import play.api.mvc.{RequestHeader, Result}
import org.elastic4play.{AuthenticationError, AuthorizationError}

abstract class Role(val name: String) {
  override def toString: String = name
}

trait AuthContext {
  def userId: String
  def userName: String
  def requestId: String
  def roles: Seq[Role]
  def authMethod: String
}

trait UserSrv {
  def getFromId(request: RequestHeader, userId: String, authMethod: String): Future[AuthContext]
  def getFromUser(request: RequestHeader, user: User, authMethod: String): Future[AuthContext]
  def getInitialUser(request: RequestHeader): Future[AuthContext]
  def inInitAuthContext[A](block: AuthContext => Future[A]): Future[A]
  def get(userId: String): Future[User]
}

trait User {
  val attributes: JsObject
  val id: String
  def getUserName: String
  def getRoles: Seq[Role]
}

object AuthCapability extends Enumeration {
  type Type = Value
  val changePassword, setPassword, authByKey = Value
}

trait AuthSrv {
  val name: String
  val capabilities = Set.empty[AuthCapability.Type]

  def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
    Future.failed(AuthenticationError("Authentication using login/password is not supported"))

  def authenticate(key: String)(implicit request: RequestHeader): Future[AuthContext] =
    Future.failed(AuthenticationError("Authentication using API key is not supported"))

  def authenticate()(implicit request: RequestHeader): Future[Either[Result, AuthContext]] =
    Future.failed(AuthenticationError("SSO authentication is not supported"))

  def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    Future.failed(AuthorizationError("Change password is not supported"))

  def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    Future.failed(AuthorizationError("Set password is not supported"))

  def renewKey(username: String)(implicit authContext: AuthContext): Future[String] =
    Future.failed(AuthorizationError("Renew API key is not supported"))
  def getKey(username: String)(implicit authContext: AuthContext): Future[String] = Future.failed(AuthorizationError("Get API key is not supported"))

  def removeKey(username: String)(implicit authContext: AuthContext): Future[Unit] =
    Future.failed(AuthorizationError("Remove API key is not supported"))
}
