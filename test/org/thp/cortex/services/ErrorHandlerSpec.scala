package org.thp.cortex.services

import org.elastic4play.{AttributeCheckingError, ConflictError, MissingAttributeError, UnknownAttributeError}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.libs.json.{JsString, Json}
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class ErrorHandlerSpec extends PlaySpecification {

  private val handler = new ErrorHandler

  "ErrorHandler.toErrorResult" should {

    "map a single attribute error to a 400 with its name" in {
      val (status, json) = handler.toErrorResult(MissingAttributeError("name"))
      status must_== BAD_REQUEST
      (json \ "type").as[String] must_== "MissingAttributeError"
      (json \ "name").as[String] must_== "name"
      (json \ "message").as[String] must contain("name")
    }

    "map an unknown configuration key to a 400" in {
      val (status, json) = handler.toErrorResult(UnknownAttributeError("worker.config", JsString("nope")))
      status must_== BAD_REQUEST
      (json \ "message").as[String] must contain("nope")
    }

    "keep aggregated attribute errors as a 400" in {
      handler.toErrorResult(AttributeCheckingError("worker", Seq(MissingAttributeError("a"))))._1 must_== BAD_REQUEST
    }

    "map a conflict to a 409 with the message and object" in {
      val (status, json) = handler.toErrorResult(ConflictError("A responder named \"x\" already exists in this organization", Json.obj("name" -> "x")))
      status must_== CONFLICT
      (json \ "type").as[String] must_== "ConflictError"
      (json \ "object" \ "name").as[String] must_== "x"
    }
  }
}
