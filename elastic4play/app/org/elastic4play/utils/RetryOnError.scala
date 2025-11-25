package org.elastic4play.utils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

import play.api.Logger

import org.apache.pekko.actor.ActorSystem

object RetryOnError {

  @deprecated("use Retry(Int, FiniteDuration)(Class[_]*)(⇒ Future[A])", "1.6.2")
  def apply[A](cond: Throwable => Boolean = _ => true, maxRetry: Int = 5, initialDelay: FiniteDuration = 1.second)(
      body: => Future[A]
  )(implicit system: ActorSystem, ec: ExecutionContext): Future[A] =
    body.recoverWith {
      case e if maxRetry > 0 && cond(e) =>
        val resultPromise = Promise[A]
        system.scheduler.scheduleOnce(initialDelay) {
          resultPromise.completeWith(apply(cond, maxRetry - 1, initialDelay * 2)(body))
        }
        resultPromise.future
    }
}

object Retry {
  val logger: Logger = Logger(getClass)

  def exceptionCheck(exceptions: Seq[Class[_]])(t: Throwable): Boolean =
    exceptions.exists(_.isAssignableFrom(t.getClass)) || Option(t.getCause).exists(exceptionCheck(exceptions))

  def apply[T](maxRetry: Int = 5, initialDelay: FiniteDuration = 1.second)(
      exceptions: Class[_]*
  )(body: => Future[T])(implicit system: ActorSystem, ec: ExecutionContext): Future[T] =
    body.recoverWith {
      case e: Throwable if maxRetry > 0 && exceptionCheck(exceptions)(e) =>
        logger.warn(s"An error occurs (${e.getMessage}), retrying ($maxRetry)")
        val resultPromise = Promise[T]()
        system.scheduler.scheduleOnce(initialDelay) {
          resultPromise.completeWith(apply(maxRetry - 1, initialDelay * 2)(exceptions: _*)(body))
        }
        resultPromise.future
      case e: Throwable if maxRetry > 0 =>
        logger.error(s"uncatch error, not retrying", e)
        throw e
    }
}
