package org.elastic4play.services

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.event.{ActorEventBus, SubchannelClassification}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.Subclassification
import org.elastic4play.models.{BaseEntity, HiveEnumeration}
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{Filter, RequestHeader, Result}

import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait EventMessage

object AuditableAction extends Enumeration with HiveEnumeration {
  type Type = Value
  val Update, Creation, Delete, Get = Value
}

case class RequestProcessStart(request: RequestHeader)                    extends EventMessage
case class RequestProcessEnd(request: RequestHeader, result: Try[Result]) extends EventMessage
case class InternalRequestProcessStart(requestId: String)                 extends EventMessage
case class InternalRequestProcessEnd(requestId: String)                   extends EventMessage

case class AuditOperation(entity: BaseEntity, action: AuditableAction.Type, details: JsObject, authContext: AuthContext, date: Date = new Date())
    extends EventMessage

@Singleton
class EventFilter @Inject() (eventSrv: EventSrv, implicit val mat: Materializer, implicit val ec: ExecutionContext) extends Filter {

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    eventSrv.publish(RequestProcessStart(requestHeader))
    nextFilter(requestHeader).andThen {
      case result => eventSrv.publish(RequestProcessEnd(requestHeader, result))
    }
  }
}

@Singleton
class EventSrv extends ActorEventBus with SubchannelClassification {
  private[EventSrv] lazy val logger = Logger(getClass)
  override type Classifier = Class[_ <: EventMessage]
  override type Event      = EventMessage

  override protected def classify(event: EventMessage): Classifier                = event.getClass
  override protected def publish(event: EventMessage, subscriber: ActorRef): Unit = subscriber ! event

  implicit protected def subclassification: Subclassification[Classifier] = new Subclassification[Classifier] {
    def isEqual(x: Classifier, y: Classifier): Boolean    = x == y
    def isSubclass(x: Classifier, y: Classifier): Boolean = y.isAssignableFrom(x)
  }
}
