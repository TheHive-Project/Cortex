package org.elastic4play

import java.util.Date

import scala.util.{Failure, Success, Try}

import scala.reflect.ClassTag

import play.api.libs.json._

import org.elastic4play.controllers.JsonFormat.inputValueFormat

object JsonFormat {
  val datePattern                       = "yyyyMMdd'T'HHmmssZ"
  private val dateReads: Reads[Date]    = Reads.dateReads(datePattern).orElse(Reads.DefaultDateReads).orElse(Reads.LongReads.map(new Date(_)))
  private val dateWrites: Writes[Date]  = Writes[Date](d => JsNumber(d.getTime))
  implicit val dateFormat: Format[Date] = Format(dateReads, dateWrites)

  private def attributeErrorWrites[E <: AttributeError](underlying: OWrites[E]): OWrites[E] = {
    OWrites.transform(underlying) { (origErr, obj) =>
      obj + ("message" -> JsString(origErr.toString))
    }
  }

  private[elastic4play] implicit val invalidFormatAttributeErrorWrites: OWrites[InvalidFormatAttributeError] = attributeErrorWrites[InvalidFormatAttributeError](Json.writes[InvalidFormatAttributeError])

  private[elastic4play] implicit val unknownAttributeErrorWrites: OWrites[UnknownAttributeError] = attributeErrorWrites[UnknownAttributeError](Json.writes[UnknownAttributeError])

  private[elastic4play] implicit val updateReadOnlyAttributeErrorWrites: OWrites[UpdateReadOnlyAttributeError] = attributeErrorWrites[UpdateReadOnlyAttributeError](Json.writes[UpdateReadOnlyAttributeError])

  private[elastic4play] implicit val missingAttributeErrorWrites: OWrites[MissingAttributeError] = attributeErrorWrites(Json.writes[MissingAttributeError])

  implicit val attributeCheckingExceptionWrites: OWrites[AttributeCheckingError] = {
    val pkgName = classOf[AttributeError].getPackage.getName + '.'

    implicit def errWrites: OWrites[AttributeError] = Json.configured(JsonConfiguration(
      discriminator = "type",
      typeNaming = JsonNaming(_.stripPrefix(pkgName))
    )).writes

    val discriminator = "type" -> JsString("AttributeCheckingError")

    Json.writes[AttributeCheckingError].transform(_ + discriminator)
  }

  implicit def tryWrites[A](implicit aWrites: Writes[A]): Writes[Try[A]] = Writes[Try[A]] {
    case Success(a) => aWrites.writes(a)
    case Failure(t) => JsString(t.getMessage)
  }
}
