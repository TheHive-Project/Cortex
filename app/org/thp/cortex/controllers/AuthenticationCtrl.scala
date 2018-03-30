package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.mvc._

import org.thp.cortex.services.UserSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import org.elastic4play.{ MissingAttributeError, Timed }
import org.elastic4play.services.JsonFormat.authContextWrites

@Singleton
class AuthenticationCtrl @Inject() (
    authSrv: AuthSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    dbIndex: DBIndex,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends AbstractController(components) {

  @Timed
  def login: Action[Fields] = Action.async(fieldsBodyParser) { implicit request ⇒
    dbIndex.getIndexStatus.flatMap {
      case false ⇒ Future.successful(Results.Status(520))
      case _ ⇒
        for {
          user ← request.body.getString("user").fold[Future[String]](Future.failed(MissingAttributeError("user")))(Future.successful)
          password ← request.body.getString("password").fold[Future[String]](Future.failed(MissingAttributeError("password")))(Future.successful)
          authContext ← authSrv.authenticate(user, password)
        } yield authenticated.setSessingUser(renderer.toOutput(OK, authContext), authContext)
    }
  }

  @Timed
  def logout = Action {
    Ok.withNewSession
  }
}