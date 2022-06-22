package org.thp.cortex.controllers

import javax.inject.{Inject, Singleton}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Random

import play.api.{Configuration, Logger}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import org.thp.cortex.models.Roles
import org.thp.cortex.services.StreamActor
import org.thp.cortex.services.StreamActor.StreamMessages

import org.elastic4play.Timed
import org.elastic4play.controllers._
import org.elastic4play.services.{AuxSrv, EventSrv, MigrationSrv, UserSrv}

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

  /** Create a new stream entry with the event head
    */
  @Timed("controllers.StreamCtrl.create")
  def create: Action[AnyContent] = authenticated(Roles.read) {
    val id = generateStreamId()
    system.actorOf(Props(classOf[StreamActor], cacheExpiration, refresh, nextItemMaxWait, globalMaxWait, eventSrv, auxSrv), s"stream-$id")
    Ok(id)
  }

  val alphanumeric: immutable.IndexedSeq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private[controllers] def generateStreamId()  = Seq.fill(10)(alphanumeric(Random.nextInt(alphanumeric.size))).mkString
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
      val futureStatus = authenticated.expirationStatus(request) match {
        case ExpirationError if !migrationSrv.isMigrating =>
          userSrv.getInitialUser(request).recoverWith { case _ => authenticated.getFromApiKey(request) }.map(_ => OK)
        case _: ExpirationWarning => Future.successful(220)
        case _                    => Future.successful(OK)
      }

      futureStatus.flatMap { status =>
        (system.actorSelection(s"/user/stream-$id") ? StreamActor.GetOperations) map {
          case StreamMessages(operations) => renderer.toOutput(status, operations)
          case m                          => InternalServerError(s"Unexpected message : $m (${m.getClass})")
        }
      }
    }
  }

  @Timed("controllers.StreamCtrl.status")
  def status: Action[AnyContent] = Action { implicit request =>
    val status = authenticated.expirationStatus(request) match {
      case ExpirationWarning(duration) => Json.obj("remaining" -> duration.toSeconds, "warning" -> true)
      case ExpirationError             => Json.obj("remaining" -> 0, "warning" -> true)
      case ExpirationOk(duration)      => Json.obj("remaining" -> duration.toSeconds, "warning" -> false)
    }
    Ok(status)
  }
}
