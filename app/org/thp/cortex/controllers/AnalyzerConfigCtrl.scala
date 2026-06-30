package org.thp.cortex.controllers

import org.elastic4play.BadRequestError
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.thp.cortex.models.{BaseConfig, Roles}
import org.thp.cortex.services.AnalyzerConfigSrv
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps

@Singleton
class AnalyzerConfigCtrl @Inject() (
    analyzerConfigSrv: AnalyzerConfigSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  private lazy val logger: Logger = Logger(getClass.getName)

  def get(analyzerConfigName: String): Action[AnyContent] = authenticated(Roles.orgAdmin).async { request =>
    analyzerConfigSrv
      .getForUser(request.userId, analyzerConfigName)
      .map(renderer.toOutput(OK, _))
  }

  def list(): Action[AnyContent] = authenticated(Roles.orgAdmin).async { request =>
    analyzerConfigSrv
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
        analyzerConfigSrv
          .updateOrCreate(request.userId, analyzerConfigName, config)
          .map(renderer.toOutput(OK, _))
          .tap(_ => logger.info(s"Analyzer $analyzerConfigName updated with $config by user id ${request.userId}"))
      case None => Future.failed(BadRequestError("attribute config has invalid format"))
    }
  }
}
