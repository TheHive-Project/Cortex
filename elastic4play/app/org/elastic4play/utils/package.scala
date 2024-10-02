package org.elastic4play

import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.{span, Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsObject, JsValue, Writes}

import akka.actor.ActorSystem
import org.scalactic.{Bad, Good, Or}

package object utils {
  implicit class RichFuture[T](future: Future[T]) {

    def withTimeout(after: FiniteDuration, default: => T)(implicit system: ActorSystem, ec: ExecutionContext): Future[T] = {
      val prom    = Promise[T]()
      val timeout = system.scheduler.scheduleOnce(after) { prom.success(default); () }
      future onComplete { _ =>
        timeout.cancel()
      }
      Future.firstCompletedOf(List(future, prom.future))
    }

    def await(implicit duration: Duration = 10 seconds span): T = Await.result(future, duration)

    def toOr[E <: Throwable](implicit evidence: Manifest[E], ec: ExecutionContext): Future[T Or E] =
      future
        .map(g => Good(g))
        .recoverWith { case evidence(error) => Future.successful(Bad(error)) }

    def toTry(implicit ec: ExecutionContext): Future[Try[T]] =
      future
        .map(r => Success(r))
        .recover { case t => Failure(t) }
  }

  implicit class RichJson(obj: JsObject) {

    def setIfAbsent[T](name: String, value: T)(implicit writes: Writes[T]): JsObject =
      if (obj.keys.contains(name))
        obj
      else
        obj + (name -> writes.writes(value))

    def mapValues(f: JsValue => JsValue): JsObject =
      JsObject(obj.fields.map {
        case (key, value) => key -> f(value)
      })

    def map(f: (String, JsValue) => (String, JsValue)): JsObject =
      obj
        .fields
        .map(kv => JsObject(Seq(f.tupled(kv))))
        .reduceOption(_ deepMerge _)
        .getOrElse(JsObject.empty)

    def collectValues(pf: PartialFunction[JsValue, JsValue]): JsObject =
      JsObject(obj.fields.collect {
        case (key, value) if pf.isDefinedAt(value) => key -> pf(value)
      })

    def collect(pf: PartialFunction[(String, JsValue), (String, JsValue)]): JsObject = JsObject(obj.fields.collect(pf))
  }

  implicit class RichOr[G, B](or: Or[G, B]) {
    def toFuture(implicit evidence: B <:< Throwable): Future[G] = or.fold(g => Future.successful(g), b => Future.failed(b))
  }

  implicit class RichTryIterable[A, Repr](xs: TraversableLike[Try[A], Repr]) {

    def partitionTry[ThatA, ThatB](implicit cbfa: CanBuildFrom[Repr, A, ThatA], cbfb: CanBuildFrom[Repr, Throwable, ThatB]): (ThatA, ThatB) = {
      val aBuilder = cbfa()
      val bBuilder = cbfb()
      xs.foreach {
        case Success(a) => aBuilder += a
        case Failure(b) => bBuilder += b
      }
      (aBuilder.result(), bBuilder.result())
    }

  }
  implicit class RichOrIterable[A, B, Repr](xs: TraversableLike[A Or B, Repr]) {

    def partitionOr[ThatA, ThatB](implicit cbfa: CanBuildFrom[Repr, A, ThatA], cbfb: CanBuildFrom[Repr, B, ThatB]): (ThatA, ThatB) = {
      val aBuilder = cbfa()
      val bBuilder = cbfb()
      xs.foreach {
        case Good(a) => aBuilder += a
        case Bad(b)  => bBuilder += b
      }
      (aBuilder.result(), bBuilder.result())
    }
  }

  implicit class RichTuble[A, B](t: (A, B)) {
    def map1[C](f: A => C): (C, B) = (f(t._1), t._2)
    def map2[C](f: B => C): (A, C) = (t._1, f(t._2))
  }
}
