package org.thp.cortex.services

import org.thp.cortex.models.{ AnalyzerNotFoundError, InvalidRequestError, JobNotFoundError, UnexpectedError }
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.{ RequestHeader, Result, Results }

import scala.concurrent.Future

class ErrorHandler extends HttpErrorHandler {
  private[ErrorHandler] lazy val logger = Logger(getClass)
  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = Future.successful {
    Results.Status(statusCode)(s"A client error occurred on ${request.method} ${request.uri} : $message")
  }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    val result = exception match {
      case JobNotFoundError(jobId)           ⇒ Results.NotFound(s"Job $jobId not found")
      case UnexpectedError(message)          ⇒ Results.InternalServerError(message)
      case AnalyzerNotFoundError(analyzerId) ⇒ Results.NotFound(s"analyzer $analyzerId not found")
      case InvalidRequestError(message)      ⇒ Results.BadRequest(message)
      case error                             ⇒ Results.InternalServerError(s"Unexpected error: $error")
    }
    Logger.info(s"${request.method} ${request.uri} returned ${result.header.status}", exception)
    Future.successful(result)
  }
}
