package models

import play.api.libs.json.JsObject
import scala.concurrent.Future
import java.util.Date
import scala.util.Success
import scala.util.Failure

object JobStatus extends Enumeration {
  type Type = Value
  val InProgress, Success, Failure = Value
}
case class Job(id: String, analyzerId: String, artifact: Artifact, report: Future[JsObject]) {
  val date: Date = new Date()

  def status: JobStatus.Type = report.value match {
    case Some(Success(x)) ⇒ (x \ "success").asOpt[Boolean] match {
      case Some(true) ⇒ JobStatus.Success
      case _          ⇒ JobStatus.Failure
    }
    case Some(Failure(_)) ⇒ JobStatus.Failure
    case None             ⇒ JobStatus.InProgress
  }
}
