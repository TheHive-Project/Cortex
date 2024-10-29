package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.longField
import com.sksamuel.elastic4s.fields.ElasticField
import org.elastic4play.controllers.{InputValue, JsonInputValue, StringInputValue}
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}
import org.scalactic._
import play.api.libs.json.{JsNumber, JsValue}

class NumberAttributeFormat extends AttributeFormat[Long]("number") {
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case _: JsNumber if subNames.isEmpty => Good(value)
    case _                               => formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Long Or Every[AttributeError] =
    if (subNames.nonEmpty)
      formatError(value)
    else
      value match {
        case StringInputValue(Seq(v)) =>
          try Good(v.toLong)
          catch {
            case _: Throwable => formatError(value)
          }
        case JsonInputValue(JsNumber(v)) => Good(v.longValue)
        case _                           => formatError(value)
      }

  override def elasticType(attributeName: String): ElasticField = longField(attributeName)

}

object NumberAttributeFormat extends NumberAttributeFormat
