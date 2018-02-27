package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import org.thp.cortex.models.Roles
import org.thp.cortex.services.{ AnalyzerConfigSrv, UserSrv }

import org.elastic4play.BadRequestError
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites

@Singleton
class AnalyzerConfigCtrl @Inject() (
    analyzerConfigSrv: AnalyzerConfigSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) {

  def get(analyzerConfigName: String): Action[AnyContent] = authenticated(Roles.orgAdmin).async { request ⇒
    analyzerConfigSrv.getForUser(request.userId, analyzerConfigName)
      .map {
        case (analyzerConfigItems, Some(analyzerConfig)) ⇒ Json.obj(
          "name" -> analyzerConfigName,
          "configurationItems" -> analyzerConfigItems,
          "config" -> analyzerConfig.jsonConfig)
        case (analyzerConfigItems, None) ⇒ Json.obj(
          "name" -> analyzerConfigName,
          "configurationItems" -> analyzerConfigItems,
          "config" -> JsObject.empty)
      }
      .map(renderer.toOutput(OK, _))
  }

  def list(): Action[AnyContent] = authenticated(Roles.orgAdmin).async { request ⇒
    analyzerConfigSrv.listForUser(request.userId)
      .map(_.map {
        case (name, (analyzerConfigItems, Some(analyzerConfig))) ⇒ Json.obj(
          "name" -> name,
          "configurationItems" -> analyzerConfigItems,
          "config" -> analyzerConfig.jsonConfig)
        case (name, (analyzerConfigItems, None)) ⇒ Json.obj(
          "name" -> name,
          "configurationItems" -> analyzerConfigItems,
          "config" -> JsObject.empty)
      })
      .map(renderer.toOutput(OK, _))
  }

  def update(analyzerConfigName: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request ⇒
    request.body.getValue("config").flatMap(_.asOpt[JsObject]) match {
      case Some(config) ⇒ analyzerConfigSrv.updateOrCreate(request.userId, analyzerConfigName, config)
        .map {
          case (analyzerConfigItems, updatedAnalyzerConfig) ⇒
            renderer.toOutput(OK, Json.obj(
              "name" -> analyzerConfigName,
              "configurationItems" -> analyzerConfigItems,
              "config" -> updatedAnalyzerConfig.jsonConfig))
        }
      case None ⇒ Future.failed(BadRequestError("attribute config has invalid format"))
    }
  }
}