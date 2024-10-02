package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.textField
import com.sksamuel.elastic4s.fields.ElasticField
import org.elastic4play.controllers.{InputValue, JsonInputValue, StringInputValue}
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}
import org.scalactic._
import play.api.libs.json.{JsString, JsValue}

class TextAttributeFormat extends AttributeFormat[String]("text") {
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case _: JsString if subNames.isEmpty => Good(value)
    case _                               => formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): String Or Every[AttributeError] =
    if (subNames.nonEmpty)
      formatError(value)
    else
      value match {
        case StringInputValue(Seq(v))    => Good(v)
        case JsonInputValue(JsString(v)) => Good(v)
        case _                           => formatError(value)
      }

  override def elasticType(attributeName: String): ElasticField = textField(attributeName).fielddata(true)
}

object TextAttributeFormat extends TextAttributeFormat
