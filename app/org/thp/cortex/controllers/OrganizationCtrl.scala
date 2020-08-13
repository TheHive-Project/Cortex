package org.thp.cortex.controllers

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.Logger
import play.api.http.Status
import play.api.mvc._

import org.thp.cortex.models.Roles
import org.thp.cortex.services.{OrganizationSrv, UserSrv}

import org.elastic4play.{BadRequestError, NotFoundError}
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.{aggReads, queryReads}
import org.elastic4play.services.{UserSrv => _, _}

@Singleton
class OrganizationCtrl @Inject() (
    organizationSrv: OrganizationSrv,
    authSrv: AuthSrv,
    auxSrv: AuxSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  private[OrganizationCtrl] lazy val logger = Logger(getClass)

  def create: Action[Fields] = authenticated(Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    organizationSrv
      .create(request.body)
      .map(organization => renderer.toOutput(CREATED, organization))
  }

  def get(organizationId: String): Action[Fields] = authenticated(Roles.superAdmin, Roles.orgAdmin).async(fieldsBodyParser) { implicit request =>
    val withStats = request.body.getBoolean("nstats").getOrElse(false)
    (for {
      userOrganizationId <- if (request.roles.contains(Roles.superAdmin)) Future.successful(organizationId)
      else userSrv.getOrganizationId(request.userId)
      if userOrganizationId == organizationId
      organization          <- organizationSrv.get(organizationId)
      organizationWithStats <- auxSrv(organization, 0, withStats, removeUnaudited = false)
    } yield renderer.toOutput(OK, organizationWithStats))
      .recoverWith { case _: NoSuchElementException => Future.failed(NotFoundError(s"organization $organizationId not found")) }
  }

  def update(organizationId: String): Action[Fields] = authenticated(Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    if (organizationId == "cortex")
      Future.failed(BadRequestError("Cortex organization can't be updated"))
    else
      organizationSrv.update(organizationId, request.body).map { organization =>
        renderer.toOutput(OK, organization)
      }
  }

  def delete(organizationId: String): Action[AnyContent] = authenticated(Roles.superAdmin).async { implicit request =>
    if (organizationId == "cortex")
      Future.failed(BadRequestError("Cortex organization can't be removed"))
    else
      organizationSrv
        .delete(organizationId)
        .map(_ => NoContent)
  }

  def find: Action[Fields] = authenticated(Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    val query                  = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range                  = request.body.getString("range")
    val sort                   = request.body.getStrings("sort").getOrElse(Nil)
    val withStats              = request.body.getBoolean("nstats").getOrElse(false)
    val (organizations, total) = organizationSrv.find(query, range, sort)
    val organizationWithStats  = auxSrv(organizations, 0, withStats, removeUnaudited = false)
    renderer.toOutput(OK, organizationWithStats, total)
  }

  def stats(): Action[Fields] = authenticated(Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs  = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    organizationSrv.stats(query, aggs).map(s => Ok(s))
  }
}
