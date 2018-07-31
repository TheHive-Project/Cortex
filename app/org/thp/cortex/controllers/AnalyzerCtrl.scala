package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.{ JsNumber, JsObject, JsString, Json }
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.thp.cortex.models.{ Roles, Worker, WorkerDefinition }
import org.thp.cortex.services.{ UserSrv, WorkerSrv }

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{ QueryDSL, QueryDef }

@Singleton
class AnalyzerCtrl @Inject() (
    workerSrv: WorkerSrv,
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
    val (analyzers, analyzerTotal) = workerSrv.findAnalyzersForUser(request.userId, query, range, sort)
    val enrichedAnalyzers = analyzers.mapAsync(2)(analyzerJson(isAdmin))
    renderer.toOutput(OK, enrichedAnalyzers, analyzerTotal)
  }

  def get(analyzerId: String): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    val isAdmin = request.roles.contains(Roles.orgAdmin)
    workerSrv.getForUser(request.userId, analyzerId)
      .flatMap(analyzerJson(isAdmin))
      .map(renderer.toOutput(OK, _))
  }

  private val emptyAnalyzerDefinitionJson = Json.obj(
    "version" → "0.0",
    "description" → "unknown",
    "dataTypeList" → Nil,
    "author" → "unknown",
    "url" → "unknown",
    "license" → "unknown")

  private def analyzerJson(analyzer: Worker, analyzerDefinition: Option[WorkerDefinition]) = {
    analyzer.toJson ++ analyzerDefinition.fold(emptyAnalyzerDefinitionJson) { ad ⇒
      Json.obj(
        "maxTlp" → (analyzer.config \ "max_tlp").asOpt[JsNumber],
        "maxPap" → (analyzer.config \ "max_pap").asOpt[JsNumber],
        "version" → ad.version,
        "description" → ad.description,
        "author" → ad.author,
        "url" → ad.url,
        "license" → ad.license,
        "baseConfig" → ad.baseConfiguration)
    } + ("analyzerDefinitionId" → JsString(analyzer.workerDefinitionId())) // For compatibility reason
  }

  private def analyzerJson(isAdmin: Boolean)(analyzer: Worker): Future[JsObject] = {
    workerSrv.getDefinition(analyzer.workerDefinitionId())
      .map(analyzerDefinition ⇒ analyzerJson(analyzer, Some(analyzerDefinition)))
      .recover { case _ ⇒ analyzerJson(analyzer, None) }
      .map {
        case a if isAdmin ⇒ a + ("configuration" → Json.parse(analyzer.configuration()))
        case a            ⇒ a
      }
  }

  def listForType(dataType: String): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    import org.elastic4play.services.QueryDSL._
    workerSrv.findAnalyzersForUser(request.userId, "dataTypeList" ~= dataType, Some("all"), Nil)
      ._1
      .mapAsyncUnordered(2) { analyzer ⇒
        workerSrv.getDefinition(analyzer.workerDefinitionId())
          .map(ad ⇒ analyzerJson(analyzer, Some(ad)))
      }
      .runWith(Sink.seq)
      .map(analyzers ⇒ renderer.toOutput(OK, analyzers))
  }

  def create(analyzerDefinitionId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request ⇒
    for {
      organizationId ← userSrv.getOrganizationId(request.userId)
      workerDefinition ← workerSrv.getDefinition(analyzerDefinitionId)
      analyzer ← workerSrv.create(organizationId, workerDefinition, request.body)
    } yield renderer.toOutput(CREATED, analyzerJson(analyzer, Some(workerDefinition)))
  }

  def listDefinitions: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    val (analyzers, analyzerTotal) = workerSrv.listAnalyzerDefinitions
    renderer.toOutput(OK, analyzers, analyzerTotal)
  }

  def scan: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin) { implicit request ⇒
    workerSrv.rescan()
    NoContent
  }

  def delete(analyzerId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    for {
      analyzer ← workerSrv.getForUser(request.userId, analyzerId)
      _ ← workerSrv.delete(analyzer)
    } yield NoContent
  }

  def update(analyzerId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request ⇒
    for {
      analyzer ← workerSrv.getForUser(request.userId, analyzerId)
      updatedAnalyzer ← workerSrv.update(analyzer, request.body)
      updatedAnalyzerJson ← analyzerJson(isAdmin = true)(updatedAnalyzer)
    } yield renderer.toOutput(OK, updatedAnalyzerJson)
  }
}