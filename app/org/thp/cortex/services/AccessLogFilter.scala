package org.thp.cortex.services

import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.{EssentialAction, EssentialFilter, RequestHeader}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AccessLogFilter @Inject() (errorHandler: HttpErrorHandler)(implicit ec: ExecutionContext) extends EssentialFilter {

  val logger: Logger = Logger(getClass)

  override def apply(next: EssentialAction): EssentialAction =
    (requestHeader: RequestHeader) => {
      val startTime = System.currentTimeMillis
      next(requestHeader)
        .recoverWith { case error => errorHandler.onServerError(requestHeader, error) }
        .map { result =>
          val endTime     = System.currentTimeMillis
          val requestTime = endTime - startTime

          logger.info(
            s"${requestHeader.remoteAddress} ${requestHeader.method} ${requestHeader.uri} took ${requestTime}ms and returned ${result.header.status} ${result
              .body
              .contentLength
              .fold("")(b => s"$b bytes")}"
          )
          result.withHeaders("Request-Time" -> requestTime.toString)
        }
    }
}
