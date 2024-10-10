package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.keywordField
import com.sksamuel.elastic4s.fields.ElasticField
import org.elastic4play.controllers.{InputValue, JsonInputValue, StringInputValue}
import org.elastic4play.services.DBLists
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}
import org.scalactic._
import play.api.libs.json.{JsString, JsValue}

case class ListEnumerationAttributeFormat(enumerationName: String)(dblists: DBLists) extends AttributeFormat[String](s"enumeration") {
  def items: Set[String] = dblists("list_" + enumerationName).cachedItems.map(_.mapTo[String]).toSet
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case JsString(v) if subNames.isEmpty && items.contains(v) => Good(value)
    case _                                                    => formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): String Or Every[AttributeError] =
    if (subNames.nonEmpty)
      formatError(value)
    else
      value match {
        case StringInputValue(Seq(v)) if items.contains(v)    => Good(v)
        case JsonInputValue(JsString(v)) if items.contains(v) => Good(v)
        case _                                                => formatError(value)
      }

  override def elasticType(attributeName: String): ElasticField = keywordField(attributeName)

  override def definition(dblists: DBLists, attribute: Attribute[String]): Seq[AttributeDefinition] =
    Seq(AttributeDefinition(attribute.attributeName, name, attribute.description, items.map(JsString.apply).toSeq, Nil))
}
