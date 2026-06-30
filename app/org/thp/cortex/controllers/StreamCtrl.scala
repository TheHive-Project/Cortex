package org.thp.cortex.controllers

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import org.elastic4play.Timed
import org.elastic4play.controllers._
import org.elastic4play.services.{AuxSrv, EventSrv, MigrationSrv}
import org.thp.cortex.models.Roles
import org.thp.cortex.services.StreamActor.StreamMessages
import org.thp.cortex.services.{StreamActor, UserSrv}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}

import java.security.SecureRandom
import javax.inject.{Inject, Singleton}
import scala.collection.immutable
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StreamCtrl(
    cacheExpiration: FiniteDuration,
    refresh: FiniteDuration,
    nextItemMaxWait: FiniteDuration,
    globalMaxWait: FiniteDuration,
    authenticated: Authenticated,
    renderer: Renderer,
    eventSrv: EventSrv,
    userSrv: UserSrv,
    auxSrv: AuxSrv,
    migrationSrv: MigrationSrv,
    components: ControllerComponents,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  @Inject() def this(
      configuration: Configuration,
      authenticated: Authenticated,
      renderer: Renderer,
      eventSrv: EventSrv,
      userSrv: UserSrv,
      auxSrv: AuxSrv,
      migrationSrv: MigrationSrv,
      components: ControllerComponents,
      system: ActorSystem,
      ec: ExecutionContext
  ) =
    this(
      configuration.getMillis("stream.longpolling.cache").millis,
      configuration.getMillis("stream.longpolling.refresh").millis,
      configuration.getMillis("stream.longpolling.nextItemMaxWait").millis,
      configuration.getMillis("stream.longpolling.globalMaxWait").millis,
      authenticated,
      renderer,
      eventSrv,
      userSrv,
      auxSrv,
      migrationSrv,
      components,
      system,
      ec
    )
  private[StreamCtrl] lazy val logger = Logger(getClass)

  // The bootstrap user has no User document in ES yet (initial setup / migration), so there is no
  // organization to look up. Such a stream is left unbound (None) instead of being tied to an org.
  private val initialUserId = "init"

  private def organizationId(userId: String): Future[Option[String]] =
    if (userId == initialUserId) Future.successful(None)
    else userSrv.getOrganizationId(userId).map(Some(_))

  /** Create a new stream entry with the event head
    */
  @Timed("controllers.StreamCtrl.create")
  def create: Action[AnyContent] = authenticated(Roles.read).async { request =>
    // the stream is bound to the creator's organization
    organizationId(request.userId).map { organizationId =>
      val id = generateStreamId()
      system.actorOf(
        Props(classOf[StreamActor], cacheExpiration, refresh, nextItemMaxWait, globalMaxWait, eventSrv, auxSrv, userSrv, organizationId),
        s"stream-$id"
      )
      Ok(id)
    }
  }

  val alphanumeric: immutable.IndexedSeq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private val random                           = new SecureRandom()
  private[controllers] def generateStreamId()  = Seq.fill(10)(alphanumeric(random.nextInt(alphanumeric.size))).mkString
  private[controllers] def isValidStreamId(streamId: String): Boolean =
    streamId.length == 10 && streamId.forall(alphanumeric.contains)

  /** Get events linked to the identified stream entry
    * This call waits up to "refresh", if there is no event, return empty response
    */
  @Timed("controllers.StreamCtrl.get")
  def get(id: String): Action[AnyContent] = Action.async { implicit request =>
    implicit val timeout: Timeout = Timeout(refresh + globalMaxWait + 1.second)

    if (!isValidStreamId(id)) {
      Future.successful(BadRequest("Invalid stream id"))
    } else {
      val futureOrganizationAndStatus: Future[(Option[String], Status)] =
        if (migrationSrv.isMigrating)
          Future.successful((None, Ok))
        else
          authenticated.expirationStatus(request) match {
            case ExpirationError =>
              userSrv
                .getInitialUser(request)
                .recoverWith { case _ => authenticated.getFromApiKey(request) }
                .flatMap(authContext => organizationId(authContext.userId).map(_ -> Ok))
            case _: ExpirationWarning =>
              authenticated.getContext(request).flatMap(authContext => organizationId(authContext.userId).map(_ -> new Status(220)))
            case _ =>
              authenticated.getContext(request).flatMap(authContext => organizationId(authContext.userId).map(_ -> Ok))
          }

      futureOrganizationAndStatus.flatMap {
        case (organizationId, status) =>
          (system.actorSelection(s"/user/stream-$id") ? StreamActor.GetOperations(organizationId)) map {
            case StreamMessages(operations) => renderer.toOutput(status.header.status, operations)
            case m                          => InternalServerError(s"Unexpected message : $m (${m.getClass})")
          }
      }
    }
  }

  @Timed("controllers.StreamCtrl.status")
  def status: Action[AnyContent] = Action { implicit request =>
    val status = authenticated.expirationStatus(request) match {
      case ExpirationWarning(duration) => Json.obj("remaining" -> duration.toSeconds, "warning" -> true)
      case ExpirationError             => Json.obj("remaining" -> 0, "warning"                  -> true)
      case ExpirationOk(duration)      => Json.obj("remaining" -> duration.toSeconds, "warning" -> false)
    }
    Ok(status)
  }
}
