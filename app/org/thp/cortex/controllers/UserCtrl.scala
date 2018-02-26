package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc._

import org.thp.cortex.models.{ OrganizationStatus, Roles }
import org.thp.cortex.services.{ OrganizationSrv, UserSrv }

import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.services.{ AuthSrv, QueryDSL, QueryDef }
import org.elastic4play._
import org.elastic4play.services.QueryDSL.and

@Singleton
class UserCtrl @Inject() (
    userSrv: UserSrv,
    authSrv: AuthSrv,
    organizationSrv: OrganizationSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  private[UserCtrl] lazy val logger = Logger(getClass)

  @Timed
  def create: Action[Fields] = authenticated(Roles.orgAdmin, Roles.superAdmin).async(fieldsBodyParser) { implicit request ⇒
    for {
      userOrganizationId ← userSrv.getOrganizationId(request.userId)
      organizationId = request.body.getString("organization").getOrElse(userOrganizationId)
      // Check if organization is valid
      organization ← organizationSrv.get(organizationId)
      if organization.status() == OrganizationStatus.Active &&
        (request.roles.contains(Roles.superAdmin) ||
          (userOrganizationId == organizationId &&
            !request.body.getStrings("roles").getOrElse(Nil).contains(Roles.superAdmin.toString)))
      user ← userSrv.create(request.body.set("organization", organizationId))
    } yield renderer.toOutput(CREATED, user)
  }

  @Timed
  def get(userId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    val isSuperAdmin = request.authContext.roles.contains(Roles.superAdmin)
    (for {
      user ← userSrv.get(userId)
      organizationId ← userSrv.getOrganizationId(request.userId)
      if isSuperAdmin || organizationId == user.organization()
    } yield renderer.toOutput(OK, user))
      .recoverWith {
        case _: NoSuchElementException ⇒ Future.failed(NotFoundError(s"user $userId not found"))
      }
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated().async(fieldsBodyParser) { implicit request ⇒
    val isOrgAdmin = request.authContext.roles.contains(Roles.orgAdmin)
    val isSuperAdmin = request.authContext.roles.contains(Roles.superAdmin)
    val validRoles = if (isSuperAdmin) Roles.roleNames
    else if (isOrgAdmin) Roles.roleNames.filterNot(_ == Roles.superAdmin.toString)
    else Nil
    if (id != request.authContext.userId && !isOrgAdmin && !isSuperAdmin) {
      Future.failed(AuthorizationError("You are not permitted to change user settings"))
    }
    else if (request.body.contains("password")) {
      Future.failed(AuthorizationError("You must use dedicated API (setPassword, changePassword) to update password"))
    }
    else if (request.body.contains("key")) {
      Future.failed(AuthorizationError("You must use dedicated API (renewKey, removeKey) to update key"))
    }
    else if (request.body.getStrings("role").getOrElse(Nil).exists(!validRoles.contains(_))) {
      Future.failed(AuthorizationError("You are not permitted to change user role"))
    }
    else if (request.body.contains("status") && !isOrgAdmin) {
      Future.failed(AuthorizationError("You are not permitted to change user status"))
    }
    else {
      // Check if organization is valid
      request.body.getString("organization")
        .fold(Future.successful(())) {
          case organizationId if request.authContext.roles.contains(Roles.superAdmin) ⇒ organizationSrv.get(organizationId).map(_ ⇒ ())
          case _ ⇒ Future.failed(AuthorizationError("You are not permitted to change user organization"))
        }
        .flatMap { _ ⇒ userSrv.update(id, request.body) }
        .map { user ⇒ renderer.toOutput(OK, user) }
    }
  }

  @Timed
  def setPassword(userId: String): Action[Fields] = authenticated(Roles.orgAdmin, Roles.superAdmin).async(fieldsBodyParser) { implicit request ⇒
    val isSuperAdmin = request.authContext.roles.contains(Roles.superAdmin)
    request.body.getString("password").fold(Future.failed[Result](MissingAttributeError("password"))) { password ⇒
      for {
        targetOrganization ← userSrv.getOrganizationId(userId)
        userOrganization ← userSrv.getOrganizationId(request.userId)
        if targetOrganization == userOrganization || isSuperAdmin
        _ ← authSrv.setPassword(userId, password)
      } yield NoContent
    }
      .recoverWith { case _: NoSuchElementException ⇒ Future.failed(NotFoundError(s"user $userId not found")) }
  }

  @Timed
  def changePassword(userId: String): Action[Fields] = authenticated().async(fieldsBodyParser) { implicit request ⇒
    if (userId == request.authContext.userId) {
      for {
        password ← request.body.getString("password").fold(Future.failed[String](MissingAttributeError("password")))(Future.successful)
        currentPassword ← request.body.getString("currentPassword").fold(Future.failed[String](MissingAttributeError("currentPassword")))(Future.successful)
        _ ← authSrv.changePassword(userId, currentPassword, password)
      } yield NoContent
    }
    else
      Future.failed(AuthorizationError("You can't change password of another user"))
  }

  @Timed
  def delete(userId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    val isSuperAdmin = request.authContext.roles.contains(Roles.superAdmin)
    (for {
      targetOrganization ← userSrv.getOrganizationId(userId)
      userOrganization ← userSrv.getOrganizationId(request.userId)
      if targetOrganization == userOrganization || isSuperAdmin
      _ ← userSrv.delete(userId)
    } yield NoContent)
      .recoverWith { case _: NoSuchElementException ⇒ Future.failed(NotFoundError(s"user $userId not found")) }
  }

  @Timed
  def currentUser: Action[AnyContent] = Action.async { implicit request ⇒
    for {
      authContext ← authenticated.getContext(request)
      user ← userSrv.get(authContext.userId)
      preferences = Try(Json.parse(user.preferences()))
        .getOrElse {
          logger.warn(s"User ${authContext.userId} has invalid preference format: ${user.preferences()}")
          JsObject.empty
        }
      json = user.toJson + ("preferences" → preferences)
    } yield renderer.toOutput(OK, json)
  }

  @Timed
  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val (users, total) = userSrv.findForUser(request.userId, query, range, sort)
    renderer.toOutput(OK, users, total)

  }

  def findForOrganization(organizationId: String): Action[Fields] = authenticated(Roles.orgAdmin, Roles.superAdmin).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val isSuperAdmin = request.roles.contains(Roles.superAdmin)
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val (users, total) = if (isSuperAdmin) userSrv.findForOrganization(organizationId, query, range, sort)
    else userSrv.findForUser(request.userId, and("organization" ~= organizationId, query), range, sort)
    renderer.toOutput(OK, users, total)
  }

  private def checkUserOrganization(userId1: String, userId2: String) = {
    for {
      userOrganization1 ← userSrv.getOrganizationId(userId1)
      userOrganization2 ← userSrv.getOrganizationId(userId2)
    } yield userOrganization1 == userOrganization2
  }
  @Timed
  def getKey(userId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    (if (request.roles.contains(Roles.superAdmin)) Future.successful(true)
    else checkUserOrganization(userId, request.userId))
      .flatMap {
        case true  ⇒ authSrv.getKey(userId)
        case false ⇒ Future.failed(AuthorizationError("Insufficient rights to perform this action"))
      }
      .map(Ok(_))
  }

  @Timed
  def removeKey(userId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    (if (request.roles.contains(Roles.superAdmin)) Future.successful(true)
    else checkUserOrganization(userId, request.userId))
      .flatMap {
        case true  ⇒ authSrv.removeKey(userId)
        case false ⇒ Future.failed(AuthorizationError("Insufficient rights to perform this action"))
      }
      .map(_ ⇒ Ok)
  }

  @Timed
  def renewKey(userId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request ⇒
    (if (request.roles.contains(Roles.superAdmin)) Future.successful(true)
    else checkUserOrganization(userId, request.userId))
      .flatMap {
        case true  ⇒ authSrv.renewKey(userId)
        case false ⇒ Future.failed(AuthorizationError("Insufficient rights to perform this action"))
      }
      .map(Ok(_))
  }
}