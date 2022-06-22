package org.thp.cortex.controllers

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.cortex.models.{Roles, Worker}
import org.thp.cortex.services.{UserSrv, WorkerSrv}

import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{QueryDSL, QueryDef}

@Singleton
class AnalyzerCtrl @Inject() (
    workerSrv: WorkerSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends AbstractController(components) {

  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { request =>
    val query                      = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range                      = request.body.getString("range")
    val sort                       = request.body.getStrings("sort").getOrElse(Nil)
    val isAdmin                    = request.roles.contains(Roles.orgAdmin)
    val (analyzers, analyzerTotal) = workerSrv.findAnalyzersForUser(request.userId, query, range, sort)
    renderer.toOutput(OK, analyzers.map(analyzerJson(isAdmin)), analyzerTotal)
  }

  def get(analyzerId: String): Action[AnyContent] = authenticated(Roles.read).async { request =>
    val isAdmin = request.roles.contains(Roles.orgAdmin)
    workerSrv
      .getForUser(request.userId, analyzerId)
      .map(a => renderer.toOutput(OK, analyzerJson(isAdmin)(a)))
  }

  private def analyzerJson(isAdmin: Boolean)(analyzer: Worker): JsObject =
    if (isAdmin)
      analyzer.toJson + ("configuration" -> Json.parse(analyzer.configuration())) + ("analyzerDefinitionId" -> JsString(
        analyzer.workerDefinitionId()
      ))
    else
      analyzer.toJson + ("analyzerDefinitionId" -> JsString(analyzer.workerDefinitionId()))

  def listForType(dataType: String): Action[AnyContent] = authenticated(Roles.read).async { request =>
    import org.elastic4play.services.QueryDSL._
    val (responderList, responderCount) = workerSrv.findAnalyzersForUser(request.userId, "dataTypeList" ~= dataType, Some("all"), Nil)
    renderer.toOutput(OK, responderList.map(analyzerJson(isAdmin = false)), responderCount)
  }

  def create(analyzerDefinitionId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request =>
    for {
      organizationId   <- userSrv.getOrganizationId(request.userId)
      workerDefinition <- Future.fromTry(workerSrv.getDefinition(analyzerDefinitionId))
      analyzer         <- workerSrv.create(organizationId, workerDefinition, request.body)
    } yield renderer.toOutput(CREATED, analyzerJson(isAdmin = false)(analyzer))
  }

  def listDefinitions: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { _ =>
    val (analyzers, analyzerTotal) = workerSrv.listAnalyzerDefinitions
    renderer.toOutput(OK, analyzers, analyzerTotal)
  }

  def scan: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin) { _ =>
    workerSrv.rescan()
    NoContent
  }

  def delete(analyzerId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request =>
    for {
      analyzer <- workerSrv.getForUser(request.userId, analyzerId)
      _        <- workerSrv.delete(analyzer)
    } yield NoContent
  }

  def update(analyzerId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request =>
    for {
      analyzer        <- workerSrv.getForUser(request.userId, analyzerId)
      updatedAnalyzer <- workerSrv.update(analyzer, request.body)
    } yield renderer.toOutput(OK, analyzerJson(isAdmin = true)(updatedAnalyzer))
  }
}
