package org.thp.cortex.controllers

import javax.inject.{Inject, Singleton}
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import org.elastic4play.services.JsonFormat.authContextWrites
import org.elastic4play.{AuthorizationError, MissingAttributeError, Timed}
import org.thp.cortex.models.UserStatus
import org.thp.cortex.services.UserSrv
import play.api.Configuration
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthenticationCtrl @Inject() (
    configuration: Configuration,
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
  def login: Action[Fields] = Action.async(fieldsBodyParser) { implicit request =>
    dbIndex.getIndexStatus.flatMap {
      case false => Future.successful(Results.Status(520))
      case _ =>
        for {
          user        <- request.body.getString("user").fold[Future[String]](Future.failed(MissingAttributeError("user")))(Future.successful)
          password    <- request.body.getString("password").fold[Future[String]](Future.failed(MissingAttributeError("password")))(Future.successful)
          authContext <- authSrv.authenticate(user, password)
        } yield authenticated.setSessingUser(renderer.toOutput(OK, authContext), authContext)
    }
  }

  @Timed
  def ssoLogin: Action[AnyContent] = Action.async { implicit request =>
    dbIndex.getIndexStatus.flatMap {
      case false => Future.successful(Results.Status(520))
      case _ =>
        authSrv
          .authenticate()
          .flatMap {
            case Right(authContext) =>
              userSrv.get(authContext.userId).map { user =>
                if (user.status() == UserStatus.Ok)
                  authenticated.setSessingUser(Redirect(configuration.get[String]("play.http.context").stripSuffix("/") + "/index.html"), authContext)
                else
                  throw AuthorizationError("Your account is locked")
              }
            case Left(result) => Future.successful(result)
          }
    }
  }

  @Timed
  def logout: Action[AnyContent] = Action {
    Ok.withNewSession
  }
}
