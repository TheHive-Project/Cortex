package org.thp.cortex.services

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import play.api.Logger

import akka.actor.{Actor, ActorRef}
import org.thp.cortex.models.JobStatus

import org.elastic4play.models.BaseEntity
import org.elastic4play.services._

object AuditActor {
  case class Register(jobId: String, timeout: FiniteDuration)
  case class Unregister(jobId: String, actorRef: ActorRef)
  case class JobEnded(jobId: String, status: JobStatus.Type)
}

@Singleton
class AuditActor @Inject() (eventSrv: EventSrv, implicit val ec: ExecutionContext) extends Actor {

  import AuditActor._

  object EntityExtractor {
    def unapply(e: BaseEntity) = Some((e.model, e.id, e.routing))
  }
  var registration                    = Map.empty[String, Seq[ActorRef]]
  private[AuditActor] lazy val logger = Logger(getClass)

  override def preStart(): Unit = {
    eventSrv.subscribe(self, classOf[EventMessage])
    super.preStart()
  }

  override def postStop(): Unit = {
    eventSrv.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case Register(jobId, timeout) =>
      logger.info(s"Register new listener for job $jobId ($sender)")
      val newActorList = registration.getOrElse(jobId, Nil) :+ sender
      registration += (jobId -> newActorList)
      context.system.scheduler.scheduleOnce(timeout, self, Unregister(jobId, sender))

    case Unregister(jobId, actorRef) =>
      logger.info(s"Unregister listener for job $jobId ($actorRef)")
      val newActorList = registration.getOrElse(jobId, Nil).filterNot(_ == actorRef)
      registration += (jobId -> newActorList)

    case AuditOperation(EntityExtractor(model, id, routing), action, details, authContext, date) =>
      if (model.modelName == "job" && action == AuditableAction.Update) {
        logger.info(s"Job $id has be updated (${details \ "status"})")
        val status = (details \ "status").asOpt[JobStatus.Type].getOrElse(JobStatus.InProgress)
        if (status != JobStatus.InProgress) registration.getOrElse(id, Nil).foreach { aref =>
          aref ! JobEnded(id, status)
        }
      }
  }
}
