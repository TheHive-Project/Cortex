package org.elastic4play

import java.util.Date

import scala.util.{Failure, Success, Try}

import play.api.libs.json._

import org.elastic4play.controllers.JsonFormat.inputValueFormat

object JsonFormat {
  val datePattern                       = "yyyyMMdd'T'HHmmssZ"
  private val dateReads: Reads[Date]    = Reads.dateReads(datePattern).orElse(Reads.DefaultDateReads).orElse(Reads.LongReads.map(new Date(_)))
  private val dateWrites: Writes[Date]  = Writes[Date](d => JsNumber(d.getTime))
  implicit val dateFormat: Format[Date] = Format(dateReads, dateWrites)

  private def attributeErrorWrites[E <: AttributeError](underlying: OWrites[E], tpe: String): OWrites[E] = {
    val discriminator: (String, JsValue) = "type" -> JsString(tpe)

    OWrites.transform(underlying) { (origErr, obj) =>
      obj + discriminator + ("message" -> JsString(origErr.toString))
    }
  }

  private[elastic4play] val invalidFormatAttributeErrorWrites: OWrites[InvalidFormatAttributeError] = attributeErrorWrites[InvalidFormatAttributeError](Json.writes[InvalidFormatAttributeError], "InvalidFormatAttributeError")

  private[elastic4play] val unknownAttributeErrorWrites: OWrites[UnknownAttributeError] = attributeErrorWrites[UnknownAttributeError](Json.writes[UnknownAttributeError], "UnknownAttributeError")

  private[elastic4play] val updateReadOnlyAttributeErrorWrites: OWrites[UpdateReadOnlyAttributeError] = attributeErrorWrites[UpdateReadOnlyAttributeError](Json.writes[UpdateReadOnlyAttributeError], "UpdateReadOnlyAttributeError")

  private[elastic4play] val missingAttributeErrorWrites: OWrites[MissingAttributeError] = attributeErrorWrites(Json.writes[MissingAttributeError], "MissingAttributeError")

  implicit val attributeCheckingExceptionWrites: OWrites[AttributeCheckingError] = OWrites[AttributeCheckingError] { ace =>
    Json.obj(
      "tableName" -> ace.tableName,
      "type"      -> "AttributeCheckingError",
      "errors" -> JsArray(ace.errors.map {
        case e: InvalidFormatAttributeError  => invalidFormatAttributeErrorWrites.writes(e)
        case e: UnknownAttributeError        => unknownAttributeErrorWrites.writes(e)
        case e: UpdateReadOnlyAttributeError => updateReadOnlyAttributeErrorWrites.writes(e)
        case e: MissingAttributeError        => missingAttributeErrorWrites.writes(e)
      })
    )
  }

  implicit def tryWrites[A](implicit aWrites: Writes[A]): Writes[Try[A]] = Writes[Try[A]] {
    case Success(a) => aWrites.writes(a)
    case Failure(t) => JsString(t.getMessage)
  }
}
