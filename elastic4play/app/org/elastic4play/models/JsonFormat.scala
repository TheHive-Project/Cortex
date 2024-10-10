package org.elastic4play.models

import play.api.libs.json._

object JsonFormat {
  implicit def baseModelEntityWrites[E <: BaseEntity]: Writes[E] = Writes((entity: BaseEntity) => entity.toJson)

  implicit def multiFormat[T](implicit jsFormat: Format[T]): Format[Seq[T]] = Format(Reads.seq(jsFormat), Writes.seq(jsFormat))

  private def optionReads[T](implicit jsReads: Reads[T]) = Reads[Option[T]] {
    case JsNull => JsSuccess(None)
    case json   => jsReads.reads(json).map(v => Some(v))
  }

  implicit def optionFormat[T](implicit jsFormat: Format[T]): Format[Option[T]] = Format(optionReads, Writes.OptionWrites)

  def enumReads[E <: Enumeration with HiveEnumeration](e: E): Reads[E#Value] =
    Reads((json: JsValue) =>
      json match {
        case JsString(s) =>
          import scala.util.Try
          Try(JsSuccess(e.getByName(s)))
            .orElse(Try(JsSuccess(e.getByName(s.toLowerCase))))
            .getOrElse(JsError(s"Enumeration expected of type: '${e.getClass}', but it does not appear to contain the value: '$s'"))
        case _ => JsError("String value expected")
      }
    )

  def enumWrites[E <: Enumeration]: Writes[E#Value] = Writes((v: E#Value) => JsString(v.toString))

  def enumFormat[E <: Enumeration with HiveEnumeration](e: E): Format[E#Value] =
    Format(enumReads(e), enumWrites)

  private val binaryReads = Reads.apply {
    case JsString(s) => JsSuccess(java.util.Base64.getDecoder.decode(s))
    case _           => JsError("")
  }
  private val binaryWrites = Writes.apply { bin: Array[Byte] =>
    JsString(java.util.Base64.getEncoder.encodeToString(bin))
  }
  val binaryFormats: Format[Array[Byte]] = Format(binaryReads, binaryWrites)

  implicit val attributeDefinitionWrites: OWrites[AttributeDefinition] = Json.writes[AttributeDefinition]
}
