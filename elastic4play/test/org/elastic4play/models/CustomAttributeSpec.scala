package org.elastic4play.models

import play.api.libs.json.{JsNumber, Json}
import play.api.test.PlaySpecification

import org.junit.runner.RunWith
import org.scalactic.Good
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CustomAttributeSpec extends PlaySpecification with Mockito {
  "a custom fields attribute" should {
    "accept valid JSON object" in {
      val js = Json.obj("field1" -> Json.obj("number" -> 12), "field2" -> Json.obj("string" -> "plop"), "field3" -> Json.obj("boolean" -> true))
      CustomAttributeFormat.checkJsonForCreation(Nil, js) must_=== Good(js)
    }

    "refuse invalid JSON object" in {
      val js     = Json.obj("field1" -> Json.obj("number" -> "str"), "field2" -> Json.obj("string" -> 12), "field3" -> Json.obj("boolean" -> 45))
      val result = CustomAttributeFormat.checkJsonForCreation(Nil, js)
      result.isBad must_=== true
    }

    "accept update a single field" in {
      val js = Json.obj("number" -> 14)
      CustomAttributeFormat.checkJsonForUpdate(Seq("field-name"), js) must_=== Good(js)
    }

    "accept update a single value" in {
      val js = JsNumber(15)
      CustomAttributeFormat.checkJsonForUpdate(Seq("field-name", "number"), js) must_=== Good(js)
    }

  }
}
