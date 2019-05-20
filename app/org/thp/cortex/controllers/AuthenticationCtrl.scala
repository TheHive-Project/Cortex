package org.thp.cortex.controllers

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._

import javax.inject.{Inject, Singleton}
import org.thp.cortex.models.UserStatus
import org.thp.cortex.services.UserSrv

import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import org.elastic4play.services.JsonFormat.authContextWrites
import org.elastic4play.{AuthorizationError, MissingAttributeError, OAuth2Redirect, Timed}

@Singleton
class AuthenticationCtrl @Inject()(
    authSrv: AuthSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    dbIndex: DBIndex,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  @Timed
  def login: Action[Fields] = Action.async(fieldsBodyParser) { implicit request ⇒
    dbIndex.getIndexStatus.flatMap {
      case false ⇒ Future.successful(Results.Status(520))
      case _ ⇒
        for {
          user        ← request.body.getString("user").fold[Future[String]](Future.failed(MissingAttributeError("user")))(Future.successful)
          password    ← request.body.getString("password").fold[Future[String]](Future.failed(MissingAttributeError("password")))(Future.successful)
          authContext ← authSrv.authenticate(user, password)
        } yield authenticated.setSessingUser(renderer.toOutput(OK, authContext), authContext)
    }
  }

  @Timed
  def ssoLogin: Action[AnyContent] = Action.async { implicit request ⇒
    dbIndex.getIndexStatus.flatMap {
      case false ⇒ Future.successful(Results.Status(520))
      case _ ⇒
        (for {
          authContext ← authSrv.authenticate()
          user        ← userSrv.get(authContext.userId)
        } yield {
          if (user.status() == UserStatus.Ok)
            authenticated.setSessingUser(Ok, authContext)
          else
            throw AuthorizationError("Your account is locked")
        }) recover {
          // A bit of a hack with the status code, so that Angular doesn't reject the origin
          case OAuth2Redirect(redirectUrl, qp) ⇒ Redirect(redirectUrl, qp, status = OK)
          case e                               ⇒ throw e
        }
    }
  }

  @Timed
  def logout = Action {
    Ok.withNewSession
  }
}
