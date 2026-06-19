package org.elastic4play

import play.api.libs.json._

import org.elastic4play.controllers.JsonInputValue

final class JsonFormatSpec extends org.specs2.mutable.Specification {
  "Play JSON format for Elastic".title

  "Format for attribute errors" should {
    val invalidFormatAttrErr = InvalidFormatAttributeError(
      name = "Test 1",
      format = "Foo",
      value = JsonInputValue(JsString("Input")))

    val invalidFormatAttrErrJson = Json.obj(
      "name" -> "Test 1",
      "format" -> "Foo",
      "value" -> Json.obj("type" -> "JsonInputValue", "value" -> "Input"),
      "type" -> "InvalidFormatAttributeError",
      "message" -> "Invalid format for Test 1: JsonInputValue(\"Input\"), expected Foo")

    "support InvalidFormatAttributeError" in {
      implicit def w: OWrites[InvalidFormatAttributeError] =
        JsonFormat.invalidFormatAttributeErrorWrites

      Json.toJsObject(invalidFormatAttrErr) must_=== invalidFormatAttrErrJson
    }

    val unknownAttrErr = UnknownAttributeError(
      name = "Test 2",
      value = JsString("Bar"))

    val unknownAttrErrJson = Json.obj(
      "name" -> "Test 2",
      "value" -> "Bar",
      "type" -> "UnknownAttributeError",
      "message" -> "Unknown attribute Test 2: \"Bar\"")

    "support UnknownAttributeError" in {
      implicit def w: OWrites[UnknownAttributeError] =
        JsonFormat.unknownAttributeErrorWrites

      Json.toJsObject(unknownAttrErr) must_=== unknownAttrErrJson
    }

    val updateRoAttrErr = UpdateReadOnlyAttributeError("Lorem")

    val updateRoAttrErrJson = Json.obj(
      "name" -> "Lorem",
      "type" -> "UpdateReadOnlyAttributeError",
      "message" -> "Attribute Lorem is read-only")

    "support UpdateReadOnlyAttributeError" in {
      implicit def w: OWrites[UpdateReadOnlyAttributeError] =
        JsonFormat.updateReadOnlyAttributeErrorWrites

      Json.toJsObject(updateRoAttrErr) must_=== updateRoAttrErrJson
    }

    val missingAttrErr = MissingAttributeError("Ipsum")

    val missingAttrErrJson = Json.obj(
      "name" -> "Ipsum",
      "type" -> "MissingAttributeError",
      "message" -> "Attribute Ipsum is missing")

    "support MissingAttributeError" in {
      implicit def w: OWrites[MissingAttributeError] =
        JsonFormat.missingAttributeErrorWrites

      Json.toJsObject(missingAttrErr) must_=== missingAttrErrJson
    }

    "support AttributeCheckingError" in {
      import JsonFormat.attributeCheckingExceptionWrites

      val errors = Seq(invalidFormatAttrErr, unknownAttrErr, updateRoAttrErr, missingAttrErr)

      val errorsJson = Seq(invalidFormatAttrErrJson, unknownAttrErrJson, updateRoAttrErrJson, missingAttrErrJson)

      Json.toJsObject(AttributeCheckingError(
        tableName = "Table 1",
        errors = errors)) must_=== Json.obj(
          "tableName" -> "Table 1",
          "type" -> "AttributeCheckingError",
          "errors" -> errorsJson) and {
        // Reverse errors to make sure the order is preserved
        Json.toJsObject(AttributeCheckingError(
          tableName = "Table 2",
          errors = errors.reverse)) must_=== Json.obj(
          "tableName" -> "Table 2",
            "type" -> "AttributeCheckingError",
            "errors" -> errorsJson.reverse)
      }
    }
  }
}
