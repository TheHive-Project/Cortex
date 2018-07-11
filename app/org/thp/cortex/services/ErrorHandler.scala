package org.thp.cortex.services

import org.thp.cortex.models.{ WorkerNotFoundError, JobNotFoundError, RateLimitExceeded }
import play.api.Logger
import play.api.http.{ HttpErrorHandler, Status, Writeable }
import play.api.mvc.{ RequestHeader, ResponseHeader, Result, Results }
import scala.concurrent.Future

import play.api.libs.json.{ JsNull, JsValue, Json }

import org.elasticsearch.client.transport.NoNodeAvailableException

import org.elastic4play._
import org.elastic4play.JsonFormat._
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.index.query.QueryShardException

class ErrorHandler extends HttpErrorHandler {
  private[ErrorHandler] lazy val logger = Logger(getClass)
  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = Future.successful {
    Results.Status(statusCode)(s"A client error occurred on ${request.method} ${request.uri} : $message")
  }

  def toErrorResult(ex: Throwable): Option[(Int, JsValue)] = {
    ex match {
      case AuthenticationError(message)        ⇒ Some(Status.UNAUTHORIZED → Json.obj("type" → "AuthenticationError", "message" → message))
      case AuthorizationError(message)         ⇒ Some(Status.FORBIDDEN → Json.obj("type" → "AuthorizationError", "message" → message))
      case UpdateError(_, message, attributes) ⇒ Some(Status.INTERNAL_SERVER_ERROR → Json.obj("type" → "UpdateError", "message" → message, "object" → attributes))
      case rle: RateLimitExceeded              ⇒ Some(Status.TOO_MANY_REQUESTS → Json.obj("type" → "RateLimitExceeded", "message" → rle.getMessage))
      case InternalError(message)              ⇒ Some(Status.INTERNAL_SERVER_ERROR → Json.obj("type" → "InternalError", "message" → message))
      case nfe: NumberFormatException          ⇒ Some(Status.BAD_REQUEST → Json.obj("type" → "NumberFormatException", "message" → ("Invalid format " + nfe.getMessage)))
      case NotFoundError(message)              ⇒ Some(Status.NOT_FOUND → Json.obj("type" → "NotFoundError", "message" → message))
      case BadRequestError(message)            ⇒ Some(Status.BAD_REQUEST → Json.obj("type" → "BadRequest", "message" → message))
      case SearchError(message, cause)         ⇒ Some(Status.BAD_REQUEST → Json.obj("type" → "SearchError", "message" → s"$message (${cause.getMessage})"))
      case ace: AttributeCheckingError         ⇒ Some(Status.BAD_REQUEST → Json.toJson(ace))
      case iae: IllegalArgumentException       ⇒ Some(Status.BAD_REQUEST → Json.obj("type" → "IllegalArgument", "message" → iae.getMessage))
      case _: NoNodeAvailableException         ⇒ Some(Status.INTERNAL_SERVER_ERROR → Json.obj("type" → "NoNodeAvailable", "message" → "ElasticSearch cluster is unreachable"))
      case CreateError(_, message, attributes) ⇒ Some(Status.INTERNAL_SERVER_ERROR → Json.obj("type" → "CreateError", "message" → message, "object" → attributes))
      case ErrorWithObject(tpe, message, obj)  ⇒ Some(Status.BAD_REQUEST → Json.obj("type" → tpe, "message" → message, "object" → obj))
      case GetError(message)                   ⇒ Some(Status.INTERNAL_SERVER_ERROR → Json.obj("type" → "GetError", "message" → message))
      case MultiError(message, exceptions) ⇒
        val suberrors = exceptions.map(e ⇒ toErrorResult(e)).collect {
          case Some((_, j)) ⇒ j
        }
        Some(Status.MULTI_STATUS → Json.obj("type" → "MultiError", "error" → message, "suberrors" → suberrors))
      case JobNotFoundError(jobId)         ⇒ Some(Status.NOT_FOUND → Json.obj("type" → "JobNotFoundError", "message" → s"Job $jobId not found"))
      case WorkerNotFoundError(analyzerId) ⇒ Some(Status.NOT_FOUND → Json.obj("type" → "AnalyzerNotFoundError", "message" → s"analyzer $analyzerId not found"))
      case _: IndexNotFoundException       ⇒ Some(520 → JsNull)
      case qse: QueryShardException        ⇒ Some(Status.BAD_REQUEST → Json.obj("type" → "Invalid search query", "message" → qse.getMessage))
      case t: Throwable                    ⇒ Option(t.getCause).flatMap(toErrorResult)
    }
  }

  def toResult[C](status: Int, c: C)(implicit writeable: Writeable[C]) = Result(header = ResponseHeader(status), body = writeable.toEntity(c))

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    val (status, body) = toErrorResult(exception).getOrElse(Status.INTERNAL_SERVER_ERROR → Json.obj("type" → exception.getClass.getName, "message" → exception.getMessage))
    logger.info(s"${request.method} ${request.uri} returned $status", exception)
    Future.successful(toResult(status, body))
  }
}
