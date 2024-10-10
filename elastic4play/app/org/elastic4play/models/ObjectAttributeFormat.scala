package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.nestedField
import com.sksamuel.elastic4s.fields.ElasticField
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicTemplateRequest
import org.elastic4play.controllers.JsonFormat.inputValueFormat
import org.elastic4play.controllers.{InputValue, JsonInputValue}
import org.elastic4play.services.DBLists
import org.elastic4play.{AttributeError, UnknownAttributeError}
import org.scalactic.Accumulation._
import org.scalactic._
import play.api.Logger
import play.api.libs.json._

case class ObjectAttributeFormat(subAttributes: Seq[Attribute[_]]) extends AttributeFormat[JsObject]("nested") {
  private[ObjectAttributeFormat] lazy val logger = Logger(getClass)

  override def checkJson(subNames: Seq[String], value: JsValue): JsObject Or Every[AttributeError] = checkJsonForCreation(subNames, value)

  override def checkJsonForCreation(subNames: Seq[String], value: JsValue): JsObject Or Every[AttributeError] = {
    val result = value match {
      case obj: JsObject if subNames.isEmpty =>
        subAttributes
          .validatedBy { attr =>
            attr.validateForCreation((value \ attr.attributeName).asOpt[JsValue])
          }
          .map { _ =>
            obj
          }
      case _ => formatError(JsonInputValue(value))
    }
    logger.debug(s"checkJsonForCreation($subNames, $value) ⇒ $result")
    result
  }

  override def checkJsonForUpdate(subNames: Seq[String], value: JsValue): JsObject Or Every[AttributeError] =
    value match {
      case obj: JsObject if subNames.isEmpty =>
        obj
          .fields
          .validatedBy {
            case (_name, v) =>
              subAttributes
                .find(_.attributeName == _name)
                .map(_.validateForUpdate(subNames, v))
                .getOrElse(Bad(One(UnknownAttributeError(_name, v))))
          }
          .map { _ =>
            obj
          }
      case _ => formatError(JsonInputValue(value))
    }

  override def fromInputValue(subNames: Seq[String], value: InputValue): JsObject Or Every[AttributeError] = {
    val result = subNames
      .headOption
      .map { subName =>
        subAttributes
          .find(_.attributeName == subName)
          .map { subAttribute =>
            value.jsonValue match {
              case jsvalue @ (JsNull | JsArray(Seq())) => Good(JsObject(Seq(subName -> jsvalue)))
              case _ =>
                subAttribute
                  .format
                  .inputValueToJson(subNames.tail, value)
                  .map(v => JsObject(Seq(subName -> v)))
                  .badMap { errors =>
                    errors.map(e => e.withName(name + "." + e.name))
                  }
            }
          }
          .getOrElse(Bad(One(UnknownAttributeError(name, value.jsonValue))))
      }
      .getOrElse {
        value match {
          case JsonInputValue(v: JsObject) =>
            v.fields
              .validatedBy {
                case (_, jsvalue) if jsvalue == JsNull || jsvalue == JsArray(Nil) => Good(jsvalue)
                case (_name, jsvalue) =>
                  subAttributes
                    .find(_.attributeName == _name)
                    .map(_.format.fromInputValue(Nil, JsonInputValue(jsvalue)))
                    .getOrElse(Bad(One(UnknownAttributeError(_name, Json.toJson(value)))))
              }
              .map { _ =>
                v
              }
          case _ => formatError(value)
        }
      }
    logger.debug(s"fromInputValue($subNames, $value) ⇒ $result")
    result
  }

  override def elasticType(attributeName: String): ElasticField = nestedField(attributeName).fields(subAttributes.map(_.elasticMapping))

  override def elasticTemplate(attributePath: Seq[String]): Seq[DynamicTemplateRequest] =
    subAttributes.flatMap(_.elasticTemplate(attributePath))

  override def definition(dblists: DBLists, attribute: Attribute[JsObject]): Seq[AttributeDefinition] =
    subAttributes
      .flatMap {
        case subAttribute: Attribute[tpe] => subAttribute.format.definition(dblists, subAttribute)
      }
      .map { attributeDefinition =>
        attributeDefinition.copy(name = s"${attribute.attributeName}.${attributeDefinition.name}")
      }
}
