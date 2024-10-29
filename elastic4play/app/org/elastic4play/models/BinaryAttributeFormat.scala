package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.binaryField
import com.sksamuel.elastic4s.fields.ElasticField
import org.elastic4play.controllers.{InputValue, JsonInputValue}
import org.elastic4play.models.JsonFormat.binaryFormats
import org.elastic4play.services.DBLists
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}
import org.scalactic._
import play.api.libs.json.JsValue

class BinaryAttributeFormat extends AttributeFormat[Array[Byte]]("binary")(binaryFormats) {
  override def checkJson(subNames: Seq[String], value: JsValue): Bad[One[InvalidFormatAttributeError]] = formatError(JsonInputValue(value))

  override def fromInputValue(subNames: Seq[String], value: InputValue): Array[Byte] Or Every[AttributeError] = formatError(value)

  override def elasticType(attributeName: String): ElasticField = binaryField(attributeName)

  override def definition(dblists: DBLists, attribute: Attribute[Array[Byte]]): Seq[AttributeDefinition] = Nil
}

object BinaryAttributeFormat extends BinaryAttributeFormat
