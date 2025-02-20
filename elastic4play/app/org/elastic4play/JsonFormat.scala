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

  private val invalidFormatAttributeErrorWrites = Writes[InvalidFormatAttributeError] { ifae =>
    Json.writes[InvalidFormatAttributeError].writes(ifae) +
      ("type"    -> JsString("InvalidFormatAttributeError")) +
      ("message" -> JsString(ifae.toString))
  }
  private val unknownAttributeErrorWrites = Writes[UnknownAttributeError] { uae =>
    Json.writes[UnknownAttributeError].writes(uae) +
      ("type"    -> JsString("UnknownAttributeError")) +
      ("message" -> JsString(uae.toString))
  }
  private val updateReadOnlyAttributeErrorWrites = Writes[UpdateReadOnlyAttributeError] { uroae =>
    Json.writes[UpdateReadOnlyAttributeError].writes(uroae) +
      ("type"    -> JsString("UpdateReadOnlyAttributeError")) +
      ("message" -> JsString(uroae.toString))
  }
  private val missingAttributeErrorWrites = Writes[MissingAttributeError] { mae =>
    Json.writes[MissingAttributeError].writes(mae) +
      ("type"    -> JsString("MissingAttributeError")) +
      ("message" -> JsString(mae.toString))
  }

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
