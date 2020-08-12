package org.thp.cortex.controllers

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.JsObject
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}
import org.thp.cortex.models.{BaseConfig, Roles}
import org.thp.cortex.services.{ResponderConfigSrv, UserSrv}

import org.elastic4play.BadRequestError
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}

@Singleton
class ResponderConfigCtrl @Inject() (
    responderConfigSrv: ResponderConfigSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  def get(analyzerConfigName: String): Action[AnyContent] = authenticated(Roles.orgAdmin).async { request =>
    responderConfigSrv
      .getForUser(request.userId, analyzerConfigName)
      .map(renderer.toOutput(OK, _))
  }

  def list(): Action[AnyContent] = authenticated(Roles.orgAdmin).async { request =>
    responderConfigSrv
      .listConfigForUser(request.userId)
      .map { bc =>
        renderer.toOutput(
          OK,
          bc.sortWith {
            case (BaseConfig("global", _, _, _), _)               => true
            case (_, BaseConfig("global", _, _, _))               => false
            case (BaseConfig(a, _, _, _), BaseConfig(b, _, _, _)) => a.compareTo(b) < 0
          }
        )
      }
  }

  def update(analyzerConfigName: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request =>
    request.body.getValue("config").flatMap(_.asOpt[JsObject]) match {
      case Some(config) =>
        responderConfigSrv
          .updateOrCreate(request.userId, analyzerConfigName, config)
          .map(renderer.toOutput(OK, _))
      case None => Future.failed(BadRequestError("attribute config has invalid format"))
    }
  }
}
