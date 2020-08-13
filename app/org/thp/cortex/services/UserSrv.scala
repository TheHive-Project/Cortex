package org.thp.cortex.services

import javax.inject.{Inject, Provider, Singleton}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.mvc.RequestHeader

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.thp.cortex.models._

import org.elastic4play.controllers.Fields
import org.elastic4play.database.{DBIndex, ModifyConfig}
import org.elastic4play.services.{User => EUser, UserSrv => EUserSrv, _}
import org.elastic4play.utils.Instance
import org.elastic4play.{AuthenticationError, AuthorizationError, NotFoundError}

@Singleton
class UserSrv(
    cacheExpiration: Duration,
    userModel: UserModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    eventSrv: EventSrv,
    authSrv: Provider[AuthSrv],
    organizationSrv: OrganizationSrv,
    dbIndex: DBIndex,
    cache: AsyncCacheApi,
    implicit val ec: ExecutionContext
) extends EUserSrv {

  @Inject() def this(
      config: Configuration,
      userModel: UserModel,
      createSrv: CreateSrv,
      getSrv: GetSrv,
      updateSrv: UpdateSrv,
      deleteSrv: DeleteSrv,
      findSrv: FindSrv,
      eventSrv: EventSrv,
      authSrv: Provider[AuthSrv],
      organizationSrv: OrganizationSrv,
      dbIndex: DBIndex,
      cache: AsyncCacheApi,
      ec: ExecutionContext
  ) =
    this(
      config.get[Duration]("cache.user"),
      userModel,
      createSrv,
      getSrv,
      updateSrv,
      deleteSrv,
      findSrv,
      eventSrv,
      authSrv,
      organizationSrv,
      dbIndex,
      cache,
      ec
    )

  private case class AuthContextImpl(userId: String, userName: String, requestId: String, roles: Seq[Role], authMethod: String) extends AuthContext

  private def invalidateCache(userId: String) = {
    cache.remove(s"user-$userId")
    cache.remove(s"user-org-$userId")
  }

  override def getFromId(request: RequestHeader, userId: String, authMethod: String): Future[AuthContext] =
    get(userId).flatMap { user =>
      getFromUser(request, user, authMethod)
    }

  override def getFromUser(request: RequestHeader, user: EUser, authMethod: String): Future[AuthContext] =
    user match {
      case u: User if u.status() == UserStatus.Ok =>
        organizationSrv.get(u.organization()).flatMap {
          case o if o.status() == OrganizationStatus.Active =>
            Future.successful(AuthContextImpl(user.id, user.getUserName, Instance.getRequestId(request), user.getRoles, authMethod))
          case _ => Future.failed(AuthorizationError("Your account is locked"))
        }
      case _ => Future.failed(AuthorizationError("Your account is locked"))
    }

  override def getInitialUser(request: RequestHeader): Future[AuthContext] =
    dbIndex.getSize(userModel.modelName).map {
      case size if size > 0 => throw AuthenticationError(s"Use of initial user is forbidden because users exist in database")
      case _                => AuthContextImpl("init", "", Instance.getRequestId(request), Roles.roles, "init")
    }

  override def inInitAuthContext[A](block: AuthContext => Future[A]): Future[A] = {
    val authContext = AuthContextImpl("init", "", Instance.getInternalId, Roles.roles, "init")
    eventSrv.publish(StreamActor.Initialize(authContext.requestId))
    block(authContext).andThen {
      case _ => eventSrv.publish(StreamActor.Commit(authContext.requestId))
    }
  }

  def create(fields: Fields)(implicit authContext: AuthContext): Future[User] =
    fields.getString("password") match {
      case None => createSrv[UserModel, User](userModel, fields)
      case Some(password) =>
        createSrv[UserModel, User](userModel, fields.unset("password")).flatMap { user =>
          authSrv.get.setPassword(user.userId(), password).map(_ => user)
        }
    }

  override def get(userId: String): Future[User] = cache.getOrElseUpdate(s"user-$userId", cacheExpiration) {
    getSrv[UserModel, User](userModel, userId)
  }

  def getOrganizationId(userId: String): Future[String] = cache.getOrElseUpdate(s"user-org-$userId", cacheExpiration) {
    get(userId).map(_.organization())
  }

  def update(userId: String, fields: Fields)(implicit Context: AuthContext): Future[User] =
    update(userId, fields, ModifyConfig.default)

  def update(userId: String, fields: Fields, modifyConfig: ModifyConfig)(implicit Context: AuthContext): Future[User] = {
    invalidateCache(userId)
    updateSrv[UserModel, User](userModel, userId, fields, modifyConfig)
  }

  def update(user: User, fields: Fields)(implicit Context: AuthContext): Future[User] =
    update(user, fields, ModifyConfig.default)

  def update(user: User, fields: Fields, modifyConfig: ModifyConfig)(implicit Context: AuthContext): Future[User] = {
    invalidateCache(user.id)
    updateSrv(user, fields, modifyConfig)
  }

  def delete(userId: String)(implicit Context: AuthContext): Future[User] = {
    invalidateCache(userId)
    deleteSrv[UserModel, User](userModel, userId)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[User, NotUsed], Future[Long]) =
    findSrv[UserModel, User](userModel, queryDef, range, sortBy)

  def findForOrganization(
      organizationId: String,
      queryDef: QueryDef,
      range: Option[String],
      sortBy: Seq[String]
  ): (Source[User, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    find(and("organization" ~= organizationId, queryDef), range, sortBy)
  }

  def findForUser(userId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[User, NotUsed], Future[Long]) = {
    val users = (for {
      user <- get(userId)
      organizationId = user.organization()
    } yield findForOrganization(organizationId, queryDef, range, sortBy))
      .recover { case NotFoundError("user init not found") => Source.empty -> Future.successful(0L) }

    val userSource = Source.futureSource(users.map(_._1)).mapMaterializedValue(_ => NotUsed)
    val userTotal  = users.flatMap(_._2)
    userSource -> userTotal
  }
}
