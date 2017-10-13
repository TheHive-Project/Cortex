package org.thp.cortex.services

import akka.actor.Actor

object Orchestrator {
  case class RegisterJob(jobId: String)
}
class Orchestrator extends Actor {
  import Orchestrator._

  override def receive: Receive = {
    case RegisterJob(jobId) ⇒

  }
}
