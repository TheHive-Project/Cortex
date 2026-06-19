package org.elastic4play

import play.api.libs.json._

import org.elastic4play.controllers.JsonInputValue

final class JsonFormatSpec extends org.specs2.mutable.Specification {
  "Play JSON format for Elastic".title

  "Format for attribute errors" should {
    "support InvalidFormatAttributeError" in {
      implicit def w: OWrites[InvalidFormatAttributeError] =
        JsonFormat.invalidFormatAttributeErrorWrites

      Json.toJsObject(InvalidFormatAttributeError(
        name = "Test 1",
        format = "Foo",
        value = JsonInputValue(JsString("Input")))) must_=== Json.obj(
          "name" -> "Test 1",
          "format" -> "Foo",
          "value" -> Json.obj("type" -> "JsonInputValue", "value" -> "Input"),
          "type" -> "InvalidFormatAttributeError",
          "message" -> "Invalid format for Test 1: JsonInputValue(\"Input\"), expected Foo")
    }

    "support UnknownAttributeError" in {
      implicit def w: OWrites[UnknownAttributeError] =
        JsonFormat.unknownAttributeErrorWrites

      Json.toJsObject(UnknownAttributeError(
        name = "Test 2",
        value = JsString("Bar"))) must_=== Json.obj(
          "name" -> "Test 2",
          "value" -> "Bar",
          "type" -> "UnknownAttributeError",
          "message" -> "Unknown attribute Test 2: \"Bar\"")
    }

    "support UpdateReadOnlyAttributeError" in {
      implicit def w: OWrites[UpdateReadOnlyAttributeError] =
        JsonFormat.updateReadOnlyAttributeErrorWrites

      Json.toJsObject(UpdateReadOnlyAttributeError("Lorem")) must_=== Json.obj(
        "name" -> "Lorem",
        "type" -> "UpdateReadOnlyAttributeError",
        "message" -> "Attribute Lorem is read-only")
    }

    "support MissingAttributeError" in {
      implicit def w: OWrites[MissingAttributeError] =
        JsonFormat.missingAttributeErrorWrites

      Json.toJsObject(MissingAttributeError("Ipsum")) must_=== Json.obj(
        "name" -> "Ipsum",
        "type" -> "MissingAttributeError",
        "message" -> "Attribute Ipsum is missing")
    }
  }
}
