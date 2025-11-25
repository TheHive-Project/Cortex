package org.elastic4play.controllers

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import play.api.http.Status
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Result, Results}

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source

import org.elastic4play.ErrorHandler

class Renderer @Inject() (errorHandler: ErrorHandler, implicit val ec: ExecutionContext, implicit val mat: Materializer) {

  def toMultiOutput[A](status: Int, objects: Seq[Try[A]])(implicit writes: Writes[A]): Result = {

    val (success, failure) = objects.foldLeft((Seq.empty[JsValue], Seq.empty[JsValue])) {
      case ((artifacts, errors), Success(a)) => (Json.toJson(a) +: artifacts, errors)
      case ((artifacts, errors), Failure(e)) =>
        val errorJson = errorHandler.toErrorResult(e) match {
          case Some((_, j)) => j
          case None         => Json.obj("type" -> e.getClass.getName, "error" -> e.getMessage)
        }
        (artifacts, errorJson +: errors)

    }
    if (failure.isEmpty)
      toOutput(status, success)
    else if (success.isEmpty)
      toOutput(Status.BAD_REQUEST, failure)
    else
      toOutput(Status.MULTI_STATUS, Json.obj("success" -> success, "failure" -> failure))
  }

  def toOutput[C](status: Int, content: C)(implicit writes: Writes[C]): Result = {
    val json = Json.toJson(content)
    val s    = new Results.Status(status)
    s(json)
  }

  def toOutput[C](status: Int, src: Source[C, _], total: Future[Long])(implicit writes: Writes[C]): Future[Result] = {
    val stringSource = src.map(s => Json.toJson(s).toString).intersperse("[", ",", "]")
    total.map { t =>
      new Results.Status(status)
        .chunked(stringSource)
        .as("application/json")
        .withHeaders("X-Total" -> t.toString)
    }
  }
}
