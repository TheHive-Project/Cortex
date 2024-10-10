package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.binaryField
import com.sksamuel.elastic4s.fields.ElasticField
import org.elastic4play.controllers.{InputValue, JsonInputValue}
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}
import org.scalactic._
import play.api.libs.json.{JsString, JsValue}

class RawAttributeFormat extends AttributeFormat[String]("raw") {
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case _: JsString if subNames.isEmpty => Good(value)
    case _                               => formatError(JsonInputValue(value))
  }
  override def fromInputValue(subNames: Seq[String], value: InputValue): String Or Every[AttributeError] =
    TextAttributeFormat.fromInputValue(subNames, value) match {
      case Bad(One(ifae: InvalidFormatAttributeError)) => Bad(One(ifae.copy(format = name)))
      case other                                       => other
    }

  override def elasticType(attributeName: String): ElasticField = binaryField(attributeName)
}

object RawAttributeFormat extends RawAttributeFormat
