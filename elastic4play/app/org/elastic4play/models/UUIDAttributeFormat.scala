package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.keywordField
import com.sksamuel.elastic4s.fields.ElasticField
import org.elastic4play.controllers.{InputValue, JsonInputValue, StringInputValue}
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}
import org.scalactic._
import play.api.libs.json.{JsString, JsValue}

import java.util.UUID

class UUIDAttributeFormat extends AttributeFormat[UUID]("uuid") {
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case JsString(v) if subNames.isEmpty =>
      try {
        UUID.fromString(v); Good(value)
      } catch {
        case _: Throwable => formatError(JsonInputValue(value))
      }
    case _ => formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): UUID Or Every[AttributeError] =
    if (subNames.nonEmpty)
      formatError(value)
    else
      value match {
        case StringInputValue(Seq(v)) =>
          try Good(UUID.fromString(v))
          catch {
            case _: Throwable => formatError(value)
          }
        case JsonInputValue(JsString(v)) =>
          try Good(UUID.fromString(v))
          catch {
            case _: Throwable => formatError(value)
          }
        case _ => formatError(value)
      }

  override def elasticType(attributeName: String): ElasticField = keywordField(attributeName)
}

object UUIDAttributeFormat extends UUIDAttributeFormat
