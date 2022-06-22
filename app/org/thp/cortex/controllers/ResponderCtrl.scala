package org.thp.cortex.controllers

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.cortex.models.{Roles, Worker, WorkerDefinition}
import org.thp.cortex.services.{UserSrv, WorkerSrv}

import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{QueryDSL, QueryDef}

@Singleton
class ResponderCtrl @Inject() (
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
    val query                        = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range                        = request.body.getString("range")
    val sort                         = request.body.getStrings("sort").getOrElse(Nil)
    val isAdmin                      = request.roles.contains(Roles.orgAdmin)
    val (responders, responderTotal) = workerSrv.findRespondersForUser(request.userId, query, range, sort)
    renderer.toOutput(OK, responders.map(responderJson(isAdmin)), responderTotal)
  }

  def get(responderId: String): Action[AnyContent] = authenticated(Roles.read).async { request =>
    val isAdmin = request.roles.contains(Roles.orgAdmin)
    workerSrv
      .getForUser(request.userId, responderId)
      .map(responder => renderer.toOutput(OK, responderJson(isAdmin)(responder)))
  }

  private val emptyResponderDefinitionJson =
    Json.obj("version" -> "0.0", "description" -> "unknown", "dataTypeList" -> Nil, "author" -> "unknown", "url" -> "unknown", "license" -> "unknown")

  private def responderJson(responder: Worker, responderDefinition: Option[WorkerDefinition]) =
    responder.toJson ++ responderDefinition.fold(emptyResponderDefinitionJson) { ad =>
      Json.obj(
        "maxTlp"      -> (responder.config \ "max_tlp").asOpt[JsNumber],
        "maxPap"      -> (responder.config \ "max_pap").asOpt[JsNumber],
        "version"     -> ad.version,
        "description" -> ad.description,
        "author"      -> ad.author,
        "url"         -> ad.url,
        "license"     -> ad.license,
        "baseConfig"  -> ad.baseConfiguration
      )
    }

  private def responderJson(isAdmin: Boolean)(responder: Worker): JsObject =
    if (isAdmin)
      responder.toJson + ("configuration" -> Json.parse(responder.configuration()))
    else
      responder.toJson

  def listForType(dataType: String): Action[AnyContent] = authenticated(Roles.read).async { request =>
    import org.elastic4play.services.QueryDSL._
    val (responderList, responderCount) = workerSrv.findRespondersForUser(request.userId, "dataTypeList" ~= dataType, Some("all"), Nil)
    renderer.toOutput(OK, responderList.map(responderJson(isAdmin = false)), responderCount)
  }

  def create(responderDefinitionId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request =>
    for {
      organizationId   <- userSrv.getOrganizationId(request.userId)
      workerDefinition <- Future.fromTry(workerSrv.getDefinition(responderDefinitionId))
      responder        <- workerSrv.create(organizationId, workerDefinition, request.body)
    } yield renderer.toOutput(CREATED, responderJson(responder, Some(workerDefinition)))
  }

  def listDefinitions: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { _ =>
    val (responders, responderTotal) = workerSrv.listResponderDefinitions
    renderer.toOutput(OK, responders, responderTotal)
  }

  def scan: Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin) { _ =>
    workerSrv.rescan()
    NoContent
  }

  def delete(responderId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request =>
    for {
      responder <- workerSrv.getForUser(request.userId, responderId)
      _         <- workerSrv.delete(responder)
    } yield NoContent
  }

  def update(responderId: String): Action[Fields] = authenticated(Roles.orgAdmin).async(fieldsBodyParser) { implicit request =>
    for {
      responder        <- workerSrv.getForUser(request.userId, responderId)
      updatedResponder <- workerSrv.update(responder, request.body)
    } yield renderer.toOutput(OK, responderJson(isAdmin = true)(updatedResponder))
  }
}
