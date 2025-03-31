package org.thp.cortex.services

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, DeadLetter, PoisonPill}
import akka.stream.Materializer
import org.elastic4play.services._
import org.elastic4play.utils.Instance
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{Filter, RequestHeader, Result}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/** This actor monitors dead messages and log them
  */
@Singleton
class DeadLetterMonitoringActor @Inject() (system: ActorSystem) extends Actor {
  private[DeadLetterMonitoringActor] lazy val logger = Logger(getClass)

  override def preStart(): Unit = {
    system.eventStream.subscribe(self, classOf[DeadLetter])
    super.preStart()
  }

  override def postStop(): Unit = {
    system.eventStream.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case DeadLetter(StreamActor.GetOperations, sender, recipient) =>
      logger.warn(s"receive dead GetOperations message, $sender → $recipient")
      sender ! StreamActor.StreamNotFound
    case other =>
      logger.error(s"receive dead message : $other")
  }
}

object StreamActor {
  /* Start of a new request identified by its id */
  case class Initialize(requestId: String) extends EventMessage
  /* Request process has finished, prepare to send associated messages */
  case class Commit(requestId: String) extends EventMessage
  /* Ask messages, wait if there is no ready messages*/
  case object GetOperations
  /* Pending messages must be sent to sender */
  case object Submit
  /* List of ready messages */
  case class StreamMessages(messages: Seq[JsObject])
  case object StreamNotFound
}

class StreamActor(
    cacheExpiration: FiniteDuration,
    refresh: FiniteDuration,
    nextItemMaxWait: FiniteDuration,
    globalMaxWait: FiniteDuration,
    eventSrv: EventSrv,
    auxSrv: AuxSrv
) extends Actor
    with ActorLogging {
  import context.dispatcher
  import org.thp.cortex.services.StreamActor._

  private[StreamActor] lazy val logger = Logger(getClass)

  private object FakeCancellable extends Cancellable {
    def cancel()    = true
    def isCancelled = true
  }

  private class WaitingRequest(senderRef: ActorRef, itemCancellable: Cancellable, globalCancellable: Cancellable, hasResult: Boolean) {
    def this(senderRef: ActorRef) = this(senderRef, FakeCancellable, context.system.scheduler.scheduleOnce(refresh, self, Submit), false)

    /** Renew timers
      */
    def renew: WaitingRequest =
      if (itemCancellable.cancel()) {
        if (!hasResult && globalCancellable.cancel()) {
          new WaitingRequest(
            senderRef,
            context.system.scheduler.scheduleOnce(nextItemMaxWait, self, Submit),
            context.system.scheduler.scheduleOnce(globalMaxWait, self, Submit),
            true
          )
        } else
          new WaitingRequest(senderRef, context.system.scheduler.scheduleOnce(nextItemMaxWait, self, Submit), globalCancellable, true)
      } else
        this

    /** Send message
      */
    def submit(messages: Seq[JsObject]): Unit = {
      itemCancellable.cancel()
      globalCancellable.cancel()
      senderRef ! StreamMessages(messages)
    }
  }

  var killCancel: Cancellable = FakeCancellable

  /** renew global timer and rearm it
    */
  def renewExpiration(): Unit =
    if (killCancel.cancel())
      killCancel = context.system.scheduler.scheduleOnce(cacheExpiration, self, PoisonPill)

  override def preStart(): Unit = {
    renewExpiration()
    eventSrv.subscribe(self, classOf[EventMessage])
  }

  override def postStop(): Unit = {
    killCancel.cancel()
    eventSrv.unsubscribe(self)
  }

  private def normalizeOperation(operation: AuditOperation) = {
    val auditedDetails = operation.details.fields.flatMap {
      case (attrName, value) =>
        val attrNames = attrName.split("\\.").toSeq
        operation
          .entity
          .model
          .attributes
          .find(a => a.attributeName == attrNames.head && !a.isUnaudited)
          .map { _ =>
            val reverseNames = attrNames.reverse
            reverseNames.drop(1).foldLeft(reverseNames.head -> value)((jsTuple, name) => name -> JsObject(Seq(jsTuple)))
          }
    }
    operation.copy(details = JsObject(auditedDetails))
  }

  private def receiveWithState(waitingRequest: Option[WaitingRequest], currentMessages: Map[String, Option[StreamMessageGroup[_]]]): Receive = {
    /* End of HTTP request, mark received messages to ready*/
    case Commit(requestId) =>
      currentMessages.get(requestId).foreach {
        case Some(message) =>
          context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (requestId -> Some(message.makeReady))))
        case None =>
      }

    /* Migration process event */
    case event: MigrationEvent =>
      val newMessages = currentMessages.get(event.modelName).flatten.fold(MigrationEventGroup(event)) {
        case e: MigrationEventGroup => e :+ event
        case _                      => MigrationEventGroup(event)
      }
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (event.modelName -> Some(newMessages))))

    /* Database migration has just finished */
    case EndOfMigrationEvent =>
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + ("end" -> Some(MigrationEventGroup.endOfMigration))))

    /* */
    case operation: AuditOperation =>
      val requestId           = operation.authContext.requestId
      val normalizedOperation = normalizeOperation(operation)
      logger.debug(s"Receiving audit operation : $operation ⇒ $normalizedOperation")
      val updatedOperationGroup = currentMessages.get(requestId) match {
        case None =>
          logger.debug("Operation that comes after the end of request, make operation ready to send")
          AuditOperationGroup(auxSrv, normalizedOperation).makeReady // Operation that comes after the end of request
        case Some(None) =>
          logger.debug("First operation of the request, creating operation group")
          AuditOperationGroup(auxSrv, normalizedOperation) // First operation related to the given request
        case Some(Some(aog: AuditOperationGroup)) =>
          logger.debug("Operation included in existing group")
          aog :+ normalizedOperation
        case _ =>
          logger.debug("Impossible")
          sys.error("")
      }
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (requestId -> Some(updatedOperationGroup))))

    case GetOperations =>
      renewExpiration()
      waitingRequest.foreach { wr =>
        wr.submit(Nil)
        logger.error("Multiple requests !")
      }
      context.become(receiveWithState(Some(new WaitingRequest(sender())), currentMessages))

    case Submit =>
      waitingRequest match {
        case Some(wr) =>
          val (readyMessages, pendingMessages) = currentMessages.partition(_._2.fold(false)(_.isReady))
          Future.sequence(readyMessages.values.map(_.get.toJson)).foreach(messages => wr.submit(messages.toSeq))
          context.become(receiveWithState(None, pendingMessages))
        case None =>
          logger.error("No request to submit !")
      }

    case Initialize(requestId) => context.become(receiveWithState(waitingRequest, currentMessages + (requestId -> None)))
    case message               => logger.warn(s"Unexpected message $message (${message.getClass})")
  }

  def receive: Receive = receiveWithState(None, Map.empty[String, Option[StreamMessageGroup[_]]])
}

@Singleton
class StreamFilter @Inject() (eventSrv: EventSrv, implicit val mat: Materializer, implicit val ec: ExecutionContext) extends Filter {

  private[StreamFilter] lazy val logger = Logger(getClass)

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val requestId = Instance.getRequestId(requestHeader)
    eventSrv.publish(StreamActor.Initialize(requestId))
    nextFilter(requestHeader).andThen {
      case _ => eventSrv.publish(StreamActor.Commit(requestId))
    }
  }
}
