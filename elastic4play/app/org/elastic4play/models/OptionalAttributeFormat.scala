package org.elastic4play.models

import com.sksamuel.elastic4s.fields.ElasticField
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicTemplateRequest
import org.elastic4play.AttributeError
import org.elastic4play.controllers.{InputValue, JsonInputValue, NullInputValue}
import org.elastic4play.models.JsonFormat.optionFormat
import org.elastic4play.services.DBLists
import org.scalactic._
import play.api.libs.json.{JsNull, JsValue}

case class OptionalAttributeFormat[T](attributeFormat: AttributeFormat[T])
    extends AttributeFormat[Option[T]]("optional-" + attributeFormat.name)(optionFormat(attributeFormat.jsFormat)) {
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, Every[AttributeError]] = value match {
    case JsNull if subNames.isEmpty => Good(value)
    case _                          => attributeFormat.checkJson(subNames, value)
  }

  override def inputValueToJson(subNames: Seq[String], value: InputValue): JsValue Or Every[AttributeError] = value match {
    case NullInputValue | JsonInputValue(JsNull) => Good(JsNull)
    case x                                       => attributeFormat.inputValueToJson(subNames, x)
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Option[T] Or Every[AttributeError] = value match {
    case NullInputValue => Good(None)
    case x              => attributeFormat.fromInputValue(subNames, x).map(v => Some(v))
  }

  override def elasticType(attributeName: String): ElasticField = attributeFormat.elasticType(attributeName)

  override def elasticTemplate(attributePath: Seq[String]): Seq[DynamicTemplateRequest] = attributeFormat.elasticTemplate(attributePath)

  override def definition(dblists: DBLists, attribute: Attribute[Option[T]]): Seq[AttributeDefinition] =
    attributeFormat.definition(dblists, attribute.asInstanceOf[Attribute[T]])
}
