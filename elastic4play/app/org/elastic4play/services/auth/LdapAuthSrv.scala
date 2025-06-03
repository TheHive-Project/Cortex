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

import org.elastic4play.services.{AuthCapability, _}
import org.elastic4play.{AuthenticationError, AuthorizationError}

case class LdapConnection(serverNames: Seq[String], useSSL: Boolean, bindDN: String, bindPW: String, baseDN: String, filter: String) {

  private[LdapConnection] lazy val logger = Logger(classOf[LdapAuthSrv])

  private val noLdapServerAvailableException = AuthenticationError("No LDAP server found")

  private def isFatal(t: Throwable): Boolean = t match {
    case null                             => true
    case `noLdapServerAvailableException` => false
    case _: ConnectException              => false
    case _                                => isFatal(t.getCause)
  }

  private def connect[A](username: String, password: String)(f: InitialDirContext => Try[A]): Try[A] =
    if (password.isEmpty) Failure(AuthenticationError("Authentication failure"))
    else
      serverNames.foldLeft[Try[A]](Failure(noLdapServerAvailableException)) {
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
      val searchResult = ctx.search(baseDN, filter, Array[Object](username), controls)
      if (searchResult.hasMore) searchResult.next().getNameInNamespace
      else throw AuthenticationError("User not found in LDAP server")
    }

  def authenticate(username: String, password: String): Try[Unit] =
    connect(bindDN, bindPW) { ctx =>
      getUserDN(ctx, username)
    }.flatMap { userDN =>
      connect(userDN, password)(_ => Success(()))
    }

  def changePassword(username: String, oldPassword: String, newPassword: String): Try[Unit] =
    connect(bindDN, bindPW) { ctx =>
      getUserDN(ctx, username)
    }.flatMap { userDN =>
      connect(userDN, oldPassword) { ctx =>
        val mods = Array(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("userPassword", newPassword)))
        Try(ctx.modifyAttributes(userDN, mods))
      }
    }
}

object LdapConnection {

  def apply(configuration: Configuration): LdapConnection =
    (for {
      bindDN <- configuration.getOptional[String]("auth.ldap.bindDN")
      bindPW <- configuration.getOptional[String]("auth.ldap.bindPW")
      baseDN <- configuration.getOptional[String]("auth.ldap.baseDN")
      filter <- configuration.getOptional[String]("auth.ldap.filter")
      serverNames = configuration.getOptional[String]("auth.ldap.serverName").fold[Seq[String]](Nil)(s => Seq(s)) ++
        configuration.getOptional[Seq[String]]("auth.ldap.serverNames").getOrElse(Nil)
      useSSL = configuration.getOptional[Boolean]("auth.ldap.useSSL").getOrElse(false)

    } yield LdapConnection(serverNames, useSSL, bindDN, bindPW, baseDN, filter))
      .getOrElse(LdapConnection(Nil, useSSL = false, "", "", "", ""))
}

@Singleton
class LdapAuthSrv(ldapConnection: LdapConnection, userSrv: UserSrv, implicit val ec: ExecutionContext) extends AuthSrv {

  @Inject() def this(configuration: Configuration, userSrv: UserSrv, ec: ExecutionContext) = this(LdapConnection(configuration), userSrv, ec)

  private[LdapAuthSrv] lazy val logger = Logger(getClass)

  val name                                             = "ldap"
  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.changePassword)

  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
    ldapConnection
      .authenticate(username, password)
      .map { _ =>
        userSrv.getFromId(request, username, name)
      }
      .fold[Future[AuthContext]](Future.failed, identity)
      .recoverWith {
        case t =>
          logger.error("LDAP authentication failure", t)
          Future.failed(AuthenticationError("Authentication failure"))
      }

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    ldapConnection
      .changePassword(username, oldPassword, newPassword)
      .fold(Future.failed, Future.successful)
      .recoverWith {
        case t =>
          logger.error("LDAP change password failure", t)
          Future.failed(AuthorizationError("Change password failure"))
      }
}
