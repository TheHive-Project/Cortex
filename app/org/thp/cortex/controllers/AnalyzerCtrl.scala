package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.{ JsNull, JsObject, Json }
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.thp.cortex.models.{ Analyzer, AnalyzerDefinition, Roles }
import org.thp.cortex.services.{ AnalyzerSrv, UserSrv }

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{ QueryDSL, QueryDef }

@Singleton
class AnalyzerCtrl @Inject() (
    analyzerSrv: AnalyzerSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends AbstractController(components) {

  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val isAdmin = request.roles.contains(Roles.orgAdmin)
    val (analyzers, analyzerTotal) = analyzerSrv.findForUser(request.userId, query, range, sort)
    val enrichedAnalyzers = analyzers.mapAsync(2)(analyzerJson(isAdmin))
    renderer.toOutput(OK, enrichedAnalyzers, analyzerTotal)
  }

  def get(analyzerId: String): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    val isAdmin = request.roles.contains(Roles.orgAdmin)
    analyzerSrv.getForUser(request.userId, analyzerId)
      .flatMap(analyzerJson(isAdmin))
      .map(renderer.toOutput(OK, _))
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
        "author" -> ad.author,
        "url" -> ad.url,
        "license" -> ad.license,
        "baseConfig" -> ad.baseConfiguration)
    }
  }

  private def analyzerJson(isAdmin: Boolean)(analyzer: Analyzer): Future[JsObject] = {
    analyzerSrv.getDefinition(analyzer.analyzerDefinitionId())
      .map(analyzerDefinition ⇒ analyzerJson(analyzer, Some(analyzerDefinition)))
      .recover { case _ ⇒ analyzerJson(analyzer, None) }
      .map {
        case a if isAdmin ⇒ a + ("configuration" -> Json.parse(analyzer.configuration()))
        case a            ⇒ a
      }
  }

  def listForType(dataType: String): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    import org.elastic4play.services.QueryDSL._
    analyzerSrv.findForUser(request.userId, "dataTypeList" ~= dataType, Some("all"), Nil)
      ._1
      .mapAsyncUnordered(2) { analyzer ⇒
        analyzerSrv.getDefinition(analyzer.analyzerDefinitionId())
          .map(ad ⇒ analyzerJson(analyzer, Some(ad)))
      }
      .runWith(Sink.seq)
      .map(analyzers ⇒ renderer.toOutput(OK, analyzers))
  }

  def create(analyzerDefinitionId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request ⇒
    for {
      organizationId ← userSrv.getOrganizationId(request.userId)
      analyzer ← analyzerSrv.create(organizationId, analyzerDefinitionId, request.body)
    } yield renderer.toOutput(CREATED, analyzer)
  }

  def listDefinitions: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    val (analyzers, analyzerTotal) = analyzerSrv.listDefinitions
    renderer.toOutput(OK, analyzers, analyzerTotal)
  }

  def scan: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin) { implicit request ⇒
    analyzerSrv.rescan()
    NoContent
  }

  def delete(analyzerId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    for {
      analyzer ← analyzerSrv.getForUser(request.userId, analyzerId)
      _ ← analyzerSrv.delete(analyzer)
    } yield NoContent
  }

  def update(analyzerId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request ⇒
    for {
      analyzer ← analyzerSrv.getForUser(request.userId, analyzerId)
      updatedAnalyzer ← analyzerSrv.update(analyzer, request.body)
    } yield renderer.toOutput(OK, updatedAnalyzer)
  }
}