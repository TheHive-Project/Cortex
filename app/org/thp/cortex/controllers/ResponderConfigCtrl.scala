package org.thp.cortex.controllers

import org.elastic4play.BadRequestError
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.thp.cortex.models.{BaseConfig, Roles}
import org.thp.cortex.services.ResponderConfigSrv
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps

@Singleton
class ResponderConfigCtrl @Inject() (
    responderConfigSrv: ResponderConfigSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  private lazy val logger: Logger = Logger(getClass.getName)

  def get(responderConfigName: String): Action[AnyContent] = authenticated(Roles.orgAdmin).async { request =>
    responderConfigSrv
      .getForUser(request.userId, responderConfigName)
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

  def update(responderConfigName: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request =>
    request.body.getValue("config").flatMap(_.asOpt[JsObject]) match {
      case Some(config) =>
        responderConfigSrv
          .updateOrCreate(request.userId, responderConfigName, config)
          .map(renderer.toOutput(OK, _))
          .tap(_ => logger.info(s"Responder $responderConfigName updated with $config by user id ${request.userId}"))
      case None => Future.failed(BadRequestError("attribute config has invalid format"))
    }
  }
}
