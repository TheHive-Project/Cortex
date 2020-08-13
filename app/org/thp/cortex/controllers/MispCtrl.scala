package org.thp.cortex.controllers

import javax.inject.Inject
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.thp.cortex.models.Roles
import org.thp.cortex.services.{MispSrv, WorkerSrv}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class MispCtrl @Inject() (
    mispSrv: MispSrv,
    analyzerSrv: WorkerSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  private[MispCtrl] lazy val logger = Logger(getClass)

  def modules: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val (analyzers, analyzerCount) = mispSrv.moduleList
    renderer.toOutput(OK, analyzers, analyzerCount)
  }

  def query: Action[JsValue] = authenticated(Roles.analyze)(parse.json).async { implicit request =>
    (request.body \ "module")
      .asOpt[String]
      .fold(Future.successful(BadRequest("Module parameter is not present in request"))) { module =>
        request
          .body
          .as[JsObject]
          .fields
          .collectFirst {
            case kv @ (k, _) if k != "module" => kv
          }
          .fold(Future.successful(BadRequest("Request doesn't contain data to analyze"))) {
            case (mispType, dataJson) =>
              dataJson.asOpt[String].fold(Future.successful(BadRequest("Data has invalid type (expected string)"))) { data =>
                mispSrv
                  .query(module, mispType, data)
                  .map(Ok(_))
              }
          }
      }
  }
}
