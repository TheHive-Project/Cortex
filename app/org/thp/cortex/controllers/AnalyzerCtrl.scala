package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.{ JsNull, JsObject, Json }
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.thp.cortex.models.{ Analyzer, AnalyzerDefinition, Roles }
import org.thp.cortex.services.AnalyzerSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.QueryDSL

@Singleton
class AnalyzerCtrl @Inject() (
    analyzerSrv: AnalyzerSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends AbstractController(components) {

  def list: Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    val (analyzers, analyzerTotal) = analyzerSrv.findForUser(request.userId, QueryDSL.any, Some("all"), Nil)
    val enrichedAnalyzers = analyzers.mapAsync(2)(analyzerJson)
    renderer.toOutput(OK, enrichedAnalyzers, analyzerTotal)
  }

  def get(analyzerId: String): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    analyzerSrv.get(analyzerId)
      .flatMap(analyzerJson)
      .map(analyzer ⇒ renderer.toOutput(OK, analyzer))

  }

  private val emptyAnalyzerDefinitionJson = Json.obj(
    "version" -> JsNull,
    "description" -> JsNull,
    "dataTypeList" -> Nil,
    "author" -> JsNull,
    "url" -> JsNull,
    "license" -> JsNull)

  private def analyzerJson(analyzer: Analyzer, analyzerDefinition: Option[AnalyzerDefinition]) = {
    analyzer.toJson ++ analyzerDefinition.fold(emptyAnalyzerDefinitionJson) { ad ⇒
      Json.obj(
        "version" -> ad.version,
        "description" -> ad.description,
        "dataTypeList" -> ad.dataTypeList,
        "author" -> ad.author,
        "url" -> ad.url,
        "license" -> ad.license)
    }
  }

  private def analyzerJson(analyzer: Analyzer): Future[JsObject] = {
    analyzerSrv.getDefinition(analyzer.analyzerDefinitionId())
      .map(analyzerDefinition ⇒ analyzerJson(analyzer, Some(analyzerDefinition)))
      .recover { case _ ⇒ analyzerJson(analyzer, None) }
  }

  def listForType(dataType: String): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    analyzerSrv.listForUser(request.userId)
      ._1
      .mapAsyncUnordered(2) { analyzer ⇒
        analyzerSrv.getDefinition(analyzer.analyzerDefinitionId()).map(analyzer -> _)
      }
      .collect {
        case (analyzer, analyzerDefinition) if analyzerDefinition.canProcessDataType(dataType) ⇒ analyzerJson(analyzer, Some(analyzerDefinition))
      }
      .runWith(Sink.seq)
      .map { analyzers ⇒ renderer.toOutput(OK, analyzers)
      }
  }

  def create(organizationId: String, analyzerDefinitionId: String): Action[Fields] = authenticated(Roles.admin).async(fieldsBodyParser) { implicit request ⇒
    analyzerSrv.create(organizationId, analyzerDefinitionId, request.body)
      .map { analyzer ⇒
        renderer.toOutput(CREATED, analyzer)
      }
  }

  def listDefinitions: Action[AnyContent] = authenticated(Roles.admin).async { request ⇒
    val (analyzerDefs, analyzerDefTotal) = analyzerSrv.listDefinitions
    renderer.toOutput(OK, analyzerDefs, analyzerDefTotal)
  }

  def scan: Action[AnyContent] = authenticated(Roles.admin) { request ⇒
    analyzerSrv.rescan
    NoContent
  }

}