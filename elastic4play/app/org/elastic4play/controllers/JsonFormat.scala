package org.elastic4play.controllers

import java.io.File
import java.nio.file.{Path, Paths}

import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import org.elastic4play.utils.Hash

object JsonFormat {

  private val fileReads = Reads[File] { json =>
    json.validate[String].map(filepath => new File(filepath))
  }
  private val fileWrites                = Writes[File]((file: File) => JsString(file.getAbsolutePath))
  implicit val fileFormat: Format[File] = Format[File](fileReads, fileWrites)

  private val pathReads = Reads[Path] { json =>
    json.validate[String].map(filepath => Paths.get(filepath))
  }
  private val pathWrites                = Writes[Path]((path: Path) => JsString(path.toString))
  implicit val pathFormat: Format[Path] = Format[Path](pathReads, pathWrites)

  private val fileInputValueWrites = Writes[FileInputValue] { (fiv: FileInputValue) =>
    fiv.jsonValue + ("type" -> JsString("FileInputValue"))
  }
  private val stringInputValueReads = Reads[StringInputValue] { json =>
    (json \ "value").validate[Seq[String]].map(s => StringInputValue(s))
  }
  private val jsonInputValueReads = Reads[JsonInputValue] { json =>
    (json \ "value").validate[JsValue].map(v => JsonInputValue(v))
  }
  private val fileInputValueReads = Reads[FileInputValue] { json =>
    for {
      name        <- (json \ "name").validate[String]
      filepath    <- (json \ "filepath").validate[Path]
      contentType <- (json \ "contentType").validate[String]
    } yield FileInputValue(name, filepath, contentType)
  }

  val attachmentInputValueReads: Reads[AttachmentInputValue] = Reads { json =>
    for {
      name        <- (json \ "name").validate[String]
      hashes      <- (json \ "hashes").validate[Seq[String]]
      size        <- (json \ "size").validate[Long]
      contentType <- (json \ "contentType").validate[String]
      id          <- (json \ "id").validate[String]
    } yield AttachmentInputValue(name, hashes.map(Hash.apply), size, contentType, id)
  }

  private val inputValueWrites = Writes[InputValue]((value: InputValue) =>
    value match {
      case v: StringInputValue     => Json.obj("type" -> "StringInputValue", "value" -> v.jsonValue)
      case v: JsonInputValue       => Json.obj("type" -> "JsonInputValue", "value" -> v.jsonValue)
      case v: FileInputValue       => Json.obj("type" -> "FileInputValue", "value" -> v.jsonValue)
      case v: AttachmentInputValue => Json.obj("type" -> "AttachmentInputValue", "value" -> v.jsonValue)
      case NullInputValue          => Json.obj("type" -> "NullInputValue")
    }
  )

  private val inputValueReads = Reads { json =>
    (json \ "type").validate[String].flatMap {
      case "StringInputValue"     => (json \ "value").validate(stringInputValueReads)
      case "JsonInputValue"       => (json \ "value").validate(jsonInputValueReads)
      case "FileInputValue"       => (json \ "value").validate(fileInputValueReads)
      case "AttachmentInputValue" => (json \ "value").validate(attachmentInputValueReads)
      case "NullInputValue"       => JsSuccess(NullInputValue)
    }
  }

  implicit val fileInputValueFormat: Format[FileInputValue] = Format[FileInputValue](fileInputValueReads, fileInputValueWrites)

  implicit val inputValueFormat: Format[InputValue] = Format[InputValue](inputValueReads, inputValueWrites)

  implicit val fieldsReader: Reads[Fields] = Reads {
    case json: JsObject => JsSuccess(Fields(json))
    case _              => JsError("Expecting JSON object body")
  }

}
