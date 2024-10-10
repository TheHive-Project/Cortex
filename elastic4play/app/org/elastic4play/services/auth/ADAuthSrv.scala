package org.elastic4play.services.auth

import java.net.ConnectException
import java.util
import javax.inject.{Inject, Singleton}
import javax.naming.Context
import javax.naming.directory._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import play.api.mvc.RequestHeader
import play.api.{Configuration, Logger}

import org.elastic4play.services._
import org.elastic4play.{AuthenticationError, AuthorizationError}

case class ADConnection(domainFQDN: String, domainName: String, serverNames: Seq[String], useSSL: Boolean) {

  private[ADConnection] lazy val logger = Logger(classOf[ADAuthSrv])

  private val noADServerAvailableException = AuthenticationError("No LDAP server found")

  private def isFatal(t: Throwable): Boolean = t match {
    case null                           => true
    case `noADServerAvailableException` => false
    case _: ConnectException            => false
    case _                              => isFatal(t.getCause)
  }

  private def connect[A](username: String, password: String)(f: InitialDirContext => Try[A]): Try[A] =
    if (password.isEmpty) Failure(AuthenticationError("Authentication failure"))
    else
      serverNames.foldLeft[Try[A]](Failure(noADServerAvailableException)) {
        case (Failure(e), serverName) if !isFatal(e) =>
          val protocol = if (useSSL) "ldaps://" else "ldap://"
          val env      = new util.Hashtable[Any, Any]
          env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
          env.put(Context.PROVIDER_URL, protocol + serverName)
          env.put(Context.SECURITY_AUTHENTICATION, "simple")
          env.put(Context.SECURITY_PRINCIPAL, username)
          env.put(Context.SECURITY_CREDENTIALS, password)
          Try {
            val ctx = new InitialDirContext(env)
            try f(ctx)
            finally ctx.close()
          }.flatten
            .recoverWith {
              case ldapError =>
                logger.debug("LDAP connect error", ldapError)
                Failure(ldapError)
            }
        case (r, _) => r
      }

  private def getUserDN(ctx: InitialDirContext, username: String): Try[String] =
    Try {
      val controls = new SearchControls()
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE)
      controls.setCountLimit(1)
      val domainDN     = domainFQDN.split("\\.").mkString("dc=", ",dc=", "")
      val searchResult = ctx.search(domainDN, "(sAMAccountName={0})", Array[Object](username), controls)
      if (searchResult.hasMore) searchResult.next().getNameInNamespace
      else throw AuthenticationError("User not found in Active Directory")
    }

  def authenticate(username: String, password: String): Try[Unit] =
    connect(domainName + "\\" + username, password)(_ => Success(()))

  def changePassword(username: String, oldPassword: String, newPassword: String): Try[Unit] =
    if (oldPassword.isEmpty || newPassword.isEmpty)
      Failure(AuthorizationError("Change password failure"))
    else {

      val unicodeOldPassword = ("\"" + oldPassword + "\"").getBytes("UTF-16LE")
      val unicodeNewPassword = ("\"" + newPassword + "\"").getBytes("UTF-16LE")
      connect(domainName + "\\" + username, oldPassword) { ctx =>
        getUserDN(ctx, username).map { userDN =>
          val mods = Array(
            new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute("unicodePwd", unicodeOldPassword)),
            new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("unicodePwd", unicodeNewPassword))
          )
          ctx.modifyAttributes(userDN, mods)
        }
      }
    }
}

object ADConnection {

  def apply(configuration: Configuration): ADConnection =
    (for {
      domainFQDN <- configuration.getOptional[String]("auth.ad.domainFQDN")
      domainName <- configuration.getOptional[String]("auth.ad.domainName")
      serverNames = configuration.getOptional[Seq[String]]("auth.ad.serverNames").getOrElse(Seq(domainFQDN))
      useSSL      = configuration.getOptional[Boolean]("auth.ad.useSSL").getOrElse(false)
    } yield ADConnection(domainFQDN, domainName, serverNames, useSSL))
      .getOrElse(ADConnection("", "", Nil, useSSL = false))
}

@Singleton
class ADAuthSrv(adConnection: ADConnection, userSrv: UserSrv, implicit val ec: ExecutionContext) extends AuthSrv {

  @Inject() def this(configuration: Configuration, userSrv: UserSrv, ec: ExecutionContext) = this(ADConnection(configuration), userSrv, ec)

  private[ADAuthSrv] lazy val logger                   = Logger(getClass)
  val name: String                                     = "ad"
  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.changePassword)

  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
    (for {
      _           <- Future.fromTry(adConnection.authenticate(username, password))
      authContext <- userSrv.getFromId(request, username, name)
    } yield authContext)
      .recoverWith {
        case t =>
          logger.error("AD authentication failure", t)
          Future.failed(AuthenticationError("Authentication failure"))
      }

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    Future
      .fromTry(adConnection.changePassword(username, oldPassword, newPassword))
      .recoverWith {
        case t =>
          logger.error("AD change password failure", t)
          Future.failed(AuthorizationError("Change password failure"))
      }
}
