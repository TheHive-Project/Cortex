package org.elastic4play.models

import com.sksamuel.elastic4s.fields.ElasticField
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicTemplateRequest
import org.elastic4play.AttributeError
import org.elastic4play.controllers.{InputValue, JsonInputValue, StringInputValue}
import org.elastic4play.models.JsonFormat.multiFormat
import org.elastic4play.services.DBLists
import org.scalactic.Accumulation._
import org.scalactic._
import play.api.libs.json.{JsArray, JsValue}

case class MultiAttributeFormat[T](attributeFormat: AttributeFormat[T])
    extends AttributeFormat[Seq[T]]("multi-" + attributeFormat.name)(multiFormat(attributeFormat.jsFormat)) {
  override def checkJsonForCreation(subNames: Seq[String], value: JsValue): Or[JsArray, Every[AttributeError]] = value match {
    case JsArray(values) if subNames.isEmpty => values.validatedBy(v => attributeFormat.checkJsonForCreation(Nil, v)).map(JsArray)
    case _                                   => formatError(JsonInputValue(value))
  }

  override def checkJsonForUpdate(subNames: Seq[String], value: JsValue): Or[JsArray, Every[AttributeError]] = value match {
    case JsArray(values) if subNames.isEmpty => values.validatedBy(v => attributeFormat.checkJsonForUpdate(Nil, v)).map(JsArray)
    case _                                   => formatError(JsonInputValue(value))
  }

  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsArray, Every[AttributeError]] = value match {
    case JsArray(values) if subNames.isEmpty => values.validatedBy(v => attributeFormat.checkJsonForUpdate(Nil, v)).map(JsArray)
    case _                                   => formatError(JsonInputValue(value))
  }

  override def inputValueToJson(subNames: Seq[String], value: InputValue): JsValue Or Every[AttributeError] = value match {
    case JsonInputValue(JsArray(xs)) => xs.map(x => JsonInputValue(x)).validatedBy(i => attributeFormat.inputValueToJson(subNames, i)).map(JsArray)
    case StringInputValue(xs) =>
      xs.filterNot(_.isEmpty).map(x => StringInputValue(x :: Nil)).validatedBy(i => attributeFormat.inputValueToJson(subNames, i)).map(JsArray.apply)
    case _ => formatError(value)
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Seq[T] Or Every[AttributeError] = value match {
    case JsonInputValue(JsArray(xs)) => xs.map(JsonInputValue).validatedBy(i => attributeFormat.fromInputValue(subNames, i))
    case StringInputValue(xs) =>
      xs.filterNot(_.isEmpty).map(x => StringInputValue(x :: Nil)).validatedBy(i => attributeFormat.fromInputValue(subNames, i))
    case _ => formatError(value)
  }

  override def elasticType(attributeName: String): ElasticField = attributeFormat.elasticType(attributeName)

  override def elasticTemplate(attributePath: Seq[String]): Seq[DynamicTemplateRequest] = attributeFormat.elasticTemplate(attributePath)

  override def definition(dblists: DBLists, attribute: Attribute[Seq[T]]): Seq[AttributeDefinition] =
    attributeFormat.definition(dblists, attribute.asInstanceOf[Attribute[T]])
}
