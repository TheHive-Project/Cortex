package models

import java.util.Date

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object JobStatus extends Enumeration {
  type Type = Value
  val InProgress, Success, Failure = Value
}

case class Job(id: String, analyzer: Analyzer, artifact: Artifact, report: Future[Report]) {
  val date: Date = new Date()

  def status: JobStatus.Type = report.value match {
    case Some(Success(SuccessReport(_, _, _))) ⇒ JobStatus.Success
    case Some(Success(FailureReport(_)))       ⇒ JobStatus.Failure
    case Some(Failure(_))                      ⇒ JobStatus.Failure
    case None                                  ⇒ JobStatus.InProgress
  }
}
