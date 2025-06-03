package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.fields.ElasticField
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicTemplateRequest
import org.elastic4play.AttributeError
import org.elastic4play.controllers.{InputValue, JsonInputValue}
import org.elastic4play.services.DBLists
import org.scalactic._
import play.api.Logger
import play.api.libs.json._

class CustomAttributeFormat extends AttributeFormat[JsValue]("custom") {
  private[CustomAttributeFormat] lazy val logger = Logger(getClass)

  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, Every[AttributeError]] = fromInputValue(subNames, JsonInputValue(value))

  override def checkJsonForCreation(subNames: Seq[String], value: JsValue): Or[JsValue, Every[AttributeError]] = {
    val result =
      if (subNames.isEmpty && objectIsValid(value)) Good(value)
      else formatError(JsonInputValue(value))
    logger.debug(s"checkJsonForCreation($subNames, $value) ⇒ $result")
    result
  }

  private def objectIsValid(v: JsValue) = v match {
    case JsObject(fields) => fields.values.forall(objectFieldsIsValid)
    case _                => false
  }

  private def objectFieldsIsValid(v: JsValue) = v match {
    case JsObject(fields) => fields.forall(fieldIsValid)
    case _                => false
  }

  private def fieldIsValid(f: (String, JsValue)): Boolean = f match {
    case ("number", _: JsNumber | JsNull)   => true
    case ("string", _: JsString | JsNull)   => true
    case ("date", JsString(d))              => DateAttributeFormat.parse(d).isDefined
    case ("date", JsNull)                   => true
    case ("date", _: JsNumber | JsNull)     => true
    case ("boolean", _: JsBoolean | JsNull) => true
    case ("order", _: JsNumber | JsNull)    => true
    case _                                  => false
  }

  override def checkJsonForUpdate(subNames: Seq[String], value: JsValue): Or[JsValue, Every[AttributeError]] = {
    val result = (subNames, value) match {
      case (Nil, _)         => checkJsonForCreation(subNames, value)
      case (Seq(_), v)      => if (objectFieldsIsValid(v)) Good(value) else formatError(JsonInputValue(value))
      case (Seq(_, tpe), v) => if (fieldIsValid(tpe -> v)) Good(value) else formatError(JsonInputValue(value))
      case _                => formatError(JsonInputValue(value))
    }
    logger.debug(s"checkJsonForUpdate($subNames, $value) ⇒ $result")
    result
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): JsValue Or Every[AttributeError] =
    value match {
      case JsonInputValue(v) => checkJsonForUpdate(subNames, v)
      case _                 => formatError(value)
    }

  override def elasticType(attributeName: String): ElasticField =
    nestedField(attributeName)

  override def elasticTemplate(attributePath: Seq[String] = Nil): Seq[DynamicTemplateRequest] = {
    val name = attributePath.mkString("_")
    DynamicTemplateRequest(
      name,
      nestedField(name).fields(
        longField("number"),
        keywordField("string"),
        dateField("date").format("epoch_millis||basic_date_time_no_millis"),
        booleanField("boolean"),
        longField("order")
      )
    ).pathMatch(attributePath.mkString(".") + ".*") :: Nil
  }

  override def definition(dblists: DBLists, attribute: Attribute[JsValue]): Seq[AttributeDefinition] =
    dblists("custom_fields").cachedItems.flatMap { item =>
      val itemObj = item.mapTo[JsObject]
      for {
        fieldName   <- (itemObj \ "reference").asOpt[String]
        tpe         <- (itemObj \ "type").asOpt[String]
        description <- (itemObj \ "description").asOpt[String]
        options     <- (itemObj \ "options").asOpt[Seq[JsString]]
      } yield AttributeDefinition(s"${attribute.attributeName}.$fieldName.$tpe", tpe, description, options, Nil)
    }
}

object CustomAttributeFormat extends CustomAttributeFormat
