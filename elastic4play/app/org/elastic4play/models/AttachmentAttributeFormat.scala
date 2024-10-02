package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.{keywordField, longField, nestedField}
import com.sksamuel.elastic4s.fields.NestedField
import org.elastic4play.controllers.JsonFormat._
import org.elastic4play.controllers.{AttachmentInputValue, FileInputValue, InputValue, JsonInputValue}
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services.{Attachment, DBLists}
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}
import org.scalactic._
import play.api.Logger
import play.api.libs.json.{JsValue, Json}

object AttachmentAttributeFormat extends AttributeFormat[Attachment]("attachment") {
  private[AttachmentAttributeFormat] lazy val logger = Logger(getClass)

  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = {
    lazy val validJson = fileInputValueFormat.reads(value).asOpt orElse jsFormat.reads(value).asOpt
    val result =
      if (subNames.isEmpty && validJson.isDefined)
        Good(value)
      else
        formatError(JsonInputValue(value))
    logger.debug(s"checkJson($subNames, $value) ⇒ $result")
    result
  }

  val forbiddenChar = Seq('/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':', ';')

  override def inputValueToJson(subNames: Seq[String], value: InputValue): JsValue Or Every[AttributeError] = {
    val result =
      if (subNames.nonEmpty)
        formatError(value)
      else
        value match {
          case fiv: FileInputValue if fiv.name.intersect(forbiddenChar).isEmpty        => Good(Json.toJson(fiv)(fileInputValueFormat))
          case aiv: AttachmentInputValue                                               => Good(Json.toJson(aiv.toAttachment)(jsFormat))
          case JsonInputValue(json) if attachmentInputValueReads.reads(json).isSuccess => Good(json)
          case _                                                                       => formatError(value)
        }
    logger.debug(s"inputValueToJson($subNames, $value) ⇒ $result")
    result
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Attachment Or Every[AttributeError] = {
    val result = value match {
      case JsonInputValue(json) if subNames.isEmpty =>
        attachmentInputValueReads.reads(json).map(aiv => Good(aiv.toAttachment)).getOrElse(formatError(value))
      case _ => formatError(value)
    }
    logger.debug(s"fromInputValue($subNames, $value) ⇒ $result")
    result
  }

  override def elasticType(attributeName: String): NestedField =
    nestedField(attributeName).fields(
      keywordField("name"),
      keywordField("hashes"),
      longField("size"),
      keywordField("contentType"),
      keywordField("id")
    )

  override def definition(dblists: DBLists, attribute: Attribute[Attachment]): Seq[AttributeDefinition] =
    Seq(
      AttributeDefinition(s"${attribute.attributeName}.name", "string", s"file name of ${attribute.description}", Nil, Nil),
      AttributeDefinition(s"${attribute.attributeName}.hash", "hash", s"hash of ${attribute.description}", Nil, Nil),
      AttributeDefinition(s"${attribute.attributeName}.size", "number", s"file size of ${attribute.description}", Nil, Nil),
      AttributeDefinition(s"${attribute.attributeName}.contentType", "string", s"content type of ${attribute.description}", Nil, Nil)
    )
}
