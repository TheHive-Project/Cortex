package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.Logger
import play.api.http.Status
import play.api.mvc._

import org.thp.cortex.models.Roles
import org.thp.cortex.services.OrganizationSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{ AuthSrv, AuxSrv, QueryDSL, QueryDef }

@Singleton
class OrganizationCtrl @Inject() (
    organizationSrv: OrganizationSrv,
    authSrv: AuthSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  private[OrganizationCtrl] lazy val logger = Logger(getClass)

  def create: Action[Fields] = authenticated(Roles.admin).async(fieldsBodyParser) { implicit request ⇒
    organizationSrv.create(request.body)
      .map(organization ⇒ renderer.toOutput(CREATED, organization))
  }

  def get(id: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val withStats = request.body.getBoolean("nstats").getOrElse(false)
    for {
      organization ← organizationSrv.get(id)
      organizationWithStats ← auxSrv(organization, 0, withStats, removeUnaudited = false)
    } yield renderer.toOutput(OK, organizationWithStats)
  }

  def update(id: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    organizationSrv.update(id, request.body).map { organization ⇒
      renderer.toOutput(OK, organization)
    }
  }

  def delete(id: String): Action[AnyContent] = authenticated(Roles.admin).async { implicit request ⇒
    organizationSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val withStats = request.body.getBoolean("nstats").getOrElse(false)
    val (organizations, total) = organizationSrv.find(query, range, sort)
    val organizationWithStats = auxSrv(organizations, 0, withStats, removeUnaudited = false)
    renderer.toOutput(OK, organizationWithStats, total)
  }
}