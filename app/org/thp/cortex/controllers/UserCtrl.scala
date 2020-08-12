package org.thp.cortex.controllers

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._

import org.thp.cortex.models.{OrganizationStatus, Roles}
import org.thp.cortex.services.{OrganizationSrv, UserSrv}

import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.services.{AuthContext, AuthSrv, QueryDSL, QueryDef}
import org.elastic4play._

@Singleton
class UserCtrl @Inject() (
    userSrv: UserSrv,
    authSrv: AuthSrv,
    organizationSrv: OrganizationSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  private[UserCtrl] lazy val logger = Logger(getClass)

  @Timed
  def create: Action[Fields] = authenticated(Roles.orgAdmin, Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    (for {
      userOrganizationId <- if (request.userId == "init") Future.successful("cortex") else userSrv.getOrganizationId(request.userId)
      organizationId = request.body.getString("organization").getOrElse(userOrganizationId)
      // Check if organization is valid
      organization <- organizationSrv.get(organizationId)
      if organization.status() == OrganizationStatus.Active &&
        (request.roles.contains(Roles.superAdmin) ||
          (userOrganizationId == organizationId &&
            !request.body.getStrings("roles").getOrElse(Nil).contains(Roles.superAdmin.name)))
      user <- userSrv.create(request.body.set("organization", organizationId))
    } yield renderer.toOutput(CREATED, user))
      .recoverWith {
        case _: NoSuchElementException => Future.failed(AuthorizationError("You are not authorized to perform this action"))
      }
  }

  @Timed
  def get(userId: String): Action[AnyContent] = authenticated(Roles.read, Roles.superAdmin).async { implicit request =>
    val isSuperAdmin = request.authContext.roles.contains(Roles.superAdmin)
    (for {
      user           <- userSrv.get(userId)
      organizationId <- userSrv.getOrganizationId(request.userId)
      if isSuperAdmin || organizationId == user.organization()
    } yield renderer.toOutput(OK, user))
      .recoverWith {
        case _: NoSuchElementException => Future.failed(NotFoundError(s"user $userId not found"))
      }
  }

  @Timed
  def update(userId: String): Action[Fields] = authenticated().async(fieldsBodyParser) { implicit request =>
    val fields = request.body

    def superAdminChecks: Future[Unit] =
      for {
        userOrganizationId <- fields.getString("organization").fold(userSrv.getOrganizationId(userId))(Future.successful)
        organization       <- organizationSrv.get(userOrganizationId)
        _ <- if (organization.status() == OrganizationStatus.Active) Future.successful(())
        else Future.failed(BadRequestError(s"Organization $userOrganizationId is locked"))
        // check roles and organization
        _ <- fields.getStrings("roles").map(_.flatMap(Roles.withName)).fold(Future.successful(())) {
          case roles if userOrganizationId == "cortex" && roles == Seq(Roles.superAdmin)    => Future.successful(())
          case roles if userOrganizationId != "cortex" && !roles.contains(Roles.superAdmin) => Future.successful(())
          case _ if userOrganizationId == "cortex"                                          => Future.failed(BadRequestError("The organization \"cortex\" can contain only superadmin users"))
          case _                                                                            => Future.failed(BadRequestError("The organization \"cortex\" alone can contain superadmin users"))
        }
        // check status
        _ <- fields.getString("status").fold(Future.successful(())) {
          case _ if userId != request.userId => Future.successful(())
          case _                             => Future.failed(BadRequestError("You can't modify your status"))
        }
      } yield ()

    def orgAdminChecks: Future[Unit] =
      for {
        subjectUserOrganization <- userSrv.getOrganizationId(request.userId)
        targetUserOrganization  <- userSrv.getOrganizationId(userId)
        _                       <- if (subjectUserOrganization == targetUserOrganization) Future.successful(()) else Future.failed(NotFoundError(s"user $userId not found"))
        // check roles
        _ <- fields.getStrings("roles").map(_.flatMap(Roles.withName)).fold(Future.successful(())) {
          case roles if !roles.contains(Roles.superAdmin) => Future.successful(())
          case _                                          => Future.failed(AuthorizationError("You can't give superadmin right to an user"))
        }
        // check organization
        _ <- if (fields.getString("organization").fold(true)(_ == targetUserOrganization)) Future.successful(())
        else Future.failed(AuthorizationError("You can't move an user to another organization"))
      } yield ()

    def userChecks: Future[Unit] =
      if (fields.contains("organization")) Future.failed(AuthorizationError("You can't change your organization"))
      else if (fields.contains("roles")) Future.failed(AuthorizationError("You can't change your role"))
      else if (fields.contains("status")) Future.failed(AuthorizationError("You can't change your status"))
      else Future.successful(())

    def authChecks: Future[Unit] =
      if (request.body.contains("password"))
        Future.failed(AuthorizationError("You must use dedicated API (setPassword, changePassword) to update password"))
      else if (request.body.contains("key")) Future.failed(AuthorizationError("You must use dedicated API (renewKey, removeKey) to update key"))
      else Future.successful(())

    for {
      _ <- if (userId == request.authContext.userId) userChecks
      else if (request.authContext.roles.contains(Roles.superAdmin)) superAdminChecks
      else if (request.authContext.roles.contains(Roles.orgAdmin)) orgAdminChecks
      else Future.failed(AuthorizationError("You are not permitted to change user settings"))
      _    <- authChecks
      user <- userSrv.update(userId, request.body)
    } yield renderer.toOutput(OK, user)
  }

  @Timed
  def setPassword(userId: String): Action[Fields] = authenticated(Roles.orgAdmin, Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    val isSuperAdmin = request.authContext.roles.contains(Roles.superAdmin)
    request
      .body
      .getString("password")
      .fold(Future.failed[Result](MissingAttributeError("password"))) { password =>
        for {
          targetOrganization <- userSrv.getOrganizationId(userId)
          userOrganization   <- userSrv.getOrganizationId(request.userId)
          if targetOrganization == userOrganization || isSuperAdmin
          _ <- authSrv.setPassword(userId, password)
        } yield NoContent
      }
      .recoverWith { case _: NoSuchElementException => Future.failed(NotFoundError(s"user $userId not found")) }
  }

  @Timed
  def changePassword(userId: String): Action[Fields] = authenticated().async(fieldsBodyParser) { implicit request =>
    if (userId == request.authContext.userId) {
      for {
        password <- request.body.getString("password").fold(Future.failed[String](MissingAttributeError("password")))(Future.successful)
        currentPassword <- request
          .body
          .getString("currentPassword")
          .fold(Future.failed[String](MissingAttributeError("currentPassword")))(Future.successful)
        _ <- authSrv.changePassword(userId, currentPassword, password)
      } yield NoContent
    } else
      Future.failed(AuthorizationError("You can't change password of another user"))
  }

  @Timed
  def delete(userId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request =>
    val isSuperAdmin = request.authContext.roles.contains(Roles.superAdmin)
    for {
      targetOrganization <- userSrv.getOrganizationId(userId)
      userOrganization   <- userSrv.getOrganizationId(request.userId)
      _ <- if (targetOrganization == userOrganization || isSuperAdmin) Future.successful(())
      else Future.failed(NotFoundError(s"user $userId not found"))
      _ <- if (userId != request.userId) Future.successful(()) else Future.failed(BadRequestError(s"You cannot disable your own account"))
      _ <- userSrv.delete(userId)
    } yield NoContent
  }

  @Timed
  def currentUser: Action[AnyContent] = Action.async { implicit request =>
    for {
      authContext <- authenticated.getContext(request)
      user        <- userSrv.get(authContext.userId)
      preferences = Try(Json.parse(user.preferences()))
        .getOrElse {
          logger.warn(s"User ${authContext.userId} has invalid preference format: ${user.preferences()}")
          JsObject.empty
        }
      json = user.toJson + ("preferences" -> preferences)
    } yield renderer.toOutput(OK, json)
  }

  @Timed
  def find: Action[Fields] = authenticated(Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    val query          = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range          = request.body.getString("range")
    val sort           = request.body.getStrings("sort").getOrElse(Nil)
    val (users, total) = userSrv.find(query, range, sort)
    renderer.toOutput(OK, users, total)

  }

  def findForOrganization(organizationId: String): Action[Fields] = authenticated(Roles.orgAdmin, Roles.superAdmin).async(fieldsBodyParser) {
    implicit request =>
      import org.elastic4play.services.QueryDSL._
      val isSuperAdmin = request.roles.contains(Roles.superAdmin)
      val query        = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
      val range        = request.body.getString("range")
      val sort         = request.body.getStrings("sort").getOrElse(Nil)
      val (users, total) =
        if (isSuperAdmin) userSrv.findForOrganization(organizationId, query, range, sort)
        else userSrv.findForUser(request.userId, and("organization" ~= organizationId, query), range, sort)
      renderer.toOutput(OK, users, total)
  }

  private def checkUserOrganization(userId: String)(implicit authContext: AuthContext): Future[Unit] =
    if (authContext.roles.contains(Roles.superAdmin)) Future.successful(())
    else
      (for {
        userOrganization1 <- userSrv.getOrganizationId(authContext.userId)
        userOrganization2 <- userSrv.getOrganizationId(userId)
        if userOrganization1 == userOrganization2
      } yield ())
        .recoverWith { case _ => Future.failed(NotFoundError(s"user $userId not found")) }

  @Timed
  def getKey(userId: String): Action[AnyContent] = authenticated().async { implicit request =>
    for {
      _ <- checkUserOrganization(userId)
      _ <- if (userId == request.userId || request.roles.contains(Roles.orgAdmin) || request.roles.contains(Roles.superAdmin)) Future.successful(())
      else Future.failed(AuthorizationError("You are not authorized to perform this operation"))
      key <- authSrv.getKey(userId)
    } yield Ok(key)
  }

  @Timed
  def removeKey(userId: String): Action[AnyContent] = authenticated(Roles.orgAdmin, Roles.superAdmin).async { implicit request =>
    for {
      _ <- checkUserOrganization(userId)
      _ <- authSrv.removeKey(userId)
    } yield NoContent
  }

  @Timed
  def renewKey(userId: String): Action[AnyContent] = authenticated().async { implicit request =>
    for {
      _ <- checkUserOrganization(userId)
      _ <- if (userId == request.userId || request.roles.contains(Roles.orgAdmin) || request.roles.contains(Roles.superAdmin)) Future.successful(())
      else Future.failed(AuthorizationError("You are not authorized to perform this operation"))
      key <- authSrv.renewKey(userId)
    } yield Ok(key)
  }
}
