package org.thp.cortex.services

import javax.inject.{Inject, Singleton}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

import play.api.{Configuration, Logger}
import play.api.mvc.RequestHeader

import org.elastic4play.services.{AuthCapability, AuthContext, AuthSrv}
import org.elastic4play.services.auth.MultiAuthSrv

object CortexAuthSrv {
  private[CortexAuthSrv] lazy val logger = Logger(getClass)

  def getAuthSrv(authTypes: Seq[String], authModules: immutable.Set[AuthSrv]): Seq[AuthSrv] =
    ("key" +: authTypes.filterNot(_ == "key"))
      .flatMap { authType =>
        authModules
          .find(_.name == authType)
          .orElse {
            logger.error(s"Authentication module $authType not found")
            None
          }
      }
}

@Singleton
class CortexAuthSrv @Inject() (
    configuration: Configuration,
    authModules: immutable.Set[AuthSrv],
    userSrv: UserSrv,
    totpSrv: TOTPSrv,
    implicit override val ec: ExecutionContext
) extends MultiAuthSrv(
      CortexAuthSrv.getAuthSrv(configuration.getDeprecated[Option[Seq[String]]]("auth.provider", "auth.type").getOrElse(Seq("local")), authModules),
      ec
    ) {

  // Recomputed from the providers rather than delegated to MultiAuthSrv: `capabilities` is a val, so
  // it can't be reached through `super`. The front-end reads this from /api/status to decide whether
  // to offer MFA enrolment in the settings page.
  override val capabilities: Set[AuthCapability.Type] = {
    val providerCapabilities = authProviders.flatMap(_.capabilities).toSet
    if (totpSrv.enabled) providerCapabilities + AuthCapability.mfa else providerCapabilities
  }

  /** Password authentication with a second factor.
    *
    * The MFA gate deliberately sits *outside* `MultiAuthSrv`'s provider fold: inside it, any failure is swallowed and falls through to the next
    * provider, which would turn a rejected MFA code into a silent retry. Here the password is checked by whichever provider owns it (local, ldap,
    * ad) and only then is the code verified, so an MFA failure propagates to the client.
    *
    * API keys and SSO are intentionally not gated: keys are how TheHive and MISP integrate, and with SSO the identity provider owns MFA.
    */
  def authenticate(username: String, password: String, code: Option[String])(implicit request: RequestHeader): Future[AuthContext] =
    super
      .authenticate(username, password)
      .flatMap { authContext =>
        userSrv
          .get(authContext.userId)
          .flatMap(user => totpSrv.checkAtLogin(user, code))
          .map(_ => authContext)
      }

  /** Password authentication with no way to supply a code — HTTP Basic auth. An enrolled user is refused rather than silently exempted. */
  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
    authenticate(username, password, None)

  // Uncomment the following lines if you want to prevent user with key to use password to authenticate.
  // NOTE: authenticate(username, password) is now overridden above, so fold this logic into that
  // override rather than pasting a second one.
  //  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
  //    userSrv.get(username)
  //      .transformWith {
  //        case Success(user) if user.key().isDefined ⇒ Future.failed(AuthenticationError("Authentication by password is not permitted for user with key"))
  //        case _: Success[_]                         ⇒ super.authenticate(username, password)
  //        case _: Failure[_]                         ⇒ Future.failed(AuthenticationError("Authentication failure"))
  //      }
}
