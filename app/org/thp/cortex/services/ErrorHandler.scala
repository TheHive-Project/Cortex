package org.thp.cortex.services

import org.elastic4play.JsonFormat.attributeCheckingExceptionWrites
import org.elastic4play._
import org.thp.cortex.models.{JobNotFoundError, RateLimitExceeded, WorkerNotFoundError}
import play.api.Logger
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, NOT_FOUND}
import play.api.http.{HttpErrorHandler, Status, Writeable}
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import play.api.mvc.{RequestHeader, ResponseHeader, Result}

import java.net.ConnectException
import scala.concurrent.Future

/** This class handles errors. It traverses all causes of exception to find known error and shows the appropriate message
  */
class ErrorHandler extends HttpErrorHandler {
  private[ErrorHandler] lazy val logger = Logger(getClass)

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val tpe = statusCode match {
      case BAD_REQUEST => "BadRequest"
      case FORBIDDEN   => "Forbidden"
      case NOT_FOUND   => "NotFound"
      case _           => "Unknown"
    }
    Future.successful(toResult(statusCode, Json.obj("type" -> tpe, "message" -> message)))
  }

  def toErrorResult(ex: Throwable): (Int, JsValue) =
    ex match {
      case AuthenticationError(message) => Status.UNAUTHORIZED -> Json.obj("type" -> "AuthenticationError", "message" -> message)
      case AuthorizationError(message)  => Status.FORBIDDEN    -> Json.obj("type" -> "AuthorizationError", "message" -> message)
      case UpdateError(_, message, attributes) =>
        Status.INTERNAL_SERVER_ERROR -> Json.obj("type" -> "UpdateError", "message" -> message, "object" -> attributes)
      case rle: RateLimitExceeded => Status.TOO_MANY_REQUESTS     -> Json.obj("type" -> "RateLimitExceeded", "message" -> rle.getMessage)
      case InternalError(message) => Status.INTERNAL_SERVER_ERROR -> Json.obj("type" -> "InternalError", "message" -> message)
      case nfe: NumberFormatException =>
        Status.BAD_REQUEST -> Json.obj("type" -> "NumberFormatException", "message" -> ("Invalid format " + nfe.getMessage))
      case NotFoundError(message)        => Status.NOT_FOUND   -> Json.obj("type" -> "NotFoundError", "message" -> message)
      case BadRequestError(message)      => Status.BAD_REQUEST -> Json.obj("type" -> "BadRequest", "message" -> message)
      case SearchError(message)          => Status.BAD_REQUEST -> Json.obj("type" -> "SearchError", "message" -> s"$message")
      case ace: AttributeCheckingError   => Status.BAD_REQUEST -> Json.toJson(ace)
      case iae: IllegalArgumentException => Status.BAD_REQUEST -> Json.obj("type" -> "IllegalArgument", "message" -> iae.getMessage)
      case _: ConnectException =>
        Status.INTERNAL_SERVER_ERROR -> Json.obj("type" -> "NoNodeAvailable", "message" -> "ElasticSearch cluster is unreachable")
      case CreateError(_, message, attributes) =>
        Status.INTERNAL_SERVER_ERROR -> Json.obj("type" -> "CreateError", "message" -> message, "object" -> attributes)
      case ErrorWithObject(tpe, message, obj) => Status.BAD_REQUEST           -> Json.obj("type" -> tpe, "message" -> message, "object" -> obj)
      case GetError(message)                  => Status.INTERNAL_SERVER_ERROR -> Json.obj("type" -> "GetError", "message" -> message)
      case MultiError(message, exceptions) =>
        val suberrors = exceptions.map(e => toErrorResult(e))
        Status.MULTI_STATUS -> Json.obj("type" -> "MultiError", "error" -> message, "suberrors" -> suberrors)
      case JobNotFoundError(jobId) => Status.NOT_FOUND -> Json.obj("type" -> "JobNotFoundError", "message" -> s"Job $jobId not found")
      case WorkerNotFoundError(analyzerId) =>
        Status.NOT_FOUND -> Json.obj("type" -> "AnalyzerNotFoundError", "message" -> s"analyzer $analyzerId not found")
      case IndexNotFoundException             => 520 -> JsNull
      case _ if Option(ex.getCause).isDefined => toErrorResult(ex.getCause)
      case _ =>
        logger.error("Internal error", ex)
        val json = Json.obj("type" -> ex.getClass.getName, "message" -> ex.getMessage)
        Status.INTERNAL_SERVER_ERROR -> (if (ex.getCause == null) json else json + ("cause" -> JsString(ex.getCause.getMessage)))
    }

  def toResult[C](status: Int, c: C)(implicit writeable: Writeable[C]): Result =
    Result(header = ResponseHeader(status), body = writeable.toEntity(c))

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    val (status, body) = toErrorResult(exception)
    if (!exception.isInstanceOf[AuthenticationError])
      if (logger.isDebugEnabled) logger.warn(s"${request.method} ${request.uri} returned $status", exception)
      else logger.warn(s"${request.method} ${request.uri} returned $status")
    Future.successful(toResult(status, body))
  }
}
