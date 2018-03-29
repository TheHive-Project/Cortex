package org.thp.cortex.controllers

import javax.inject.Inject
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.services.QueryDSL
import org.thp.cortex.models.Roles
import org.thp.cortex.services.{ AnalyzerSrv, MispSrv }
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

class MispCtrl @Inject() (
    mispSrv: MispSrv,
    analyzerSrv: AnalyzerSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) {

  private[MispCtrl] lazy val logger = Logger(getClass)

  def modules: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒

    /*val (analyzers, analyzerCount) = analyzerSrv.findForUser(request.userId, QueryDSL.any, Some("all"), Nil)

      val mispAnalyzers = analyzers.mapAsyncUnordered(1) { analyzer => analyzerSrv.getDefinition(analyzer.analyzerId()).map(analyzer -> _) }
      .map {
        case (analyzer, analyzerDefinition) =>
          Json.obj(
            "name" → analyzer.id,
            "type" → "cortex",
            "mispattributes" → Json.obj(
              "input" → analyzer.dataTypeList().flatMap(dataType2mispType).distinct,
              "output" → Json.arr()),
            "meta" → Json.obj(
              "module-type" → Json.arr("cortex"),
              "description" → analyzer.description(),
              "author" → analyzerDefinition.author,
              "version" → analyzerDefinition.version,
              "config" → Json.arr()))
      }
*/
    val (analyzers, analyzerCount) = mispSrv.moduleList
    renderer.toOutput(OK, analyzers, analyzerCount)
  }

  def query: Action[JsValue] = authenticated(Roles.analyze)(parse.json).async { implicit request ⇒
    (request.body \ "module").asOpt[String]
      .fold(Future.successful(BadRequest("Module parameter is not present in request"))) { module ⇒
        request.body.as[JsObject].fields
          .collectFirst {
            case kv @ (k, _) if k != "module" ⇒ kv
          }
          .fold(Future.successful(BadRequest("Request doesn't contain data to analyze"))) {
            case (mispType, dataJson) ⇒
              dataJson.asOpt[String].fold(Future.successful(BadRequest("Data has invalid type (expected string)"))) { data ⇒
                mispSrv.query(module, mispType, data)
                  .map(Ok(_))
              }
          }
      }
  }
}

