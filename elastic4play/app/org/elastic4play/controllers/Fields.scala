package org.elastic4play.controllers

import org.apache.pekko.util.ByteString
import org.elastic4play.BadRequestError
import org.elastic4play.controllers.JsonFormat.{fieldsReader, pathFormat}
import org.elastic4play.services.Attachment
import org.elastic4play.utils.Hash
import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.api.libs.streams.Accumulator
import play.api.mvc._

import java.nio.file.Path
import java.util.Locale
import javax.inject.Inject
import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * Define a data value from HTTP request. It can be simple string, json, file or null (maybe xml in future)
  */
sealed trait InputValue {
  def jsonValue: JsValue
}

/**
  * Define a data value from HTTP request as simple string
  */
case class StringInputValue(data: Seq[String]) extends InputValue {
  def jsonValue: JsValue = Json.toJson(data)
}

object StringInputValue {
  def apply(s: String): StringInputValue = this(Seq(s))
}

/**
  * Define a data value from HTTP request as json value
  */
case class JsonInputValue(data: JsValue) extends InputValue {
  def jsonValue: JsValue = data
}

/**
  * Define a data value from HTTP request as file (filename, path to temporary file and content type). Other data are lost
  */
case class FileInputValue(name: String, filepath: Path, contentType: String) extends InputValue {
  def jsonValue: JsObject = Json.obj("name" -> name, "filepath" -> filepath, "contentType" -> contentType)
}

/**
  * Define an attachment that is already in datastore. This type can't be from HTTP request.
  */
case class AttachmentInputValue(name: String, hashes: Seq[Hash], size: Long, contentType: String, id: String) extends InputValue {
  def jsonValue: JsObject      = Json.obj("name" -> name, "hashes" -> hashes.map(_.toString()), "size" -> size, "contentType" -> contentType, "id" -> id)
  def toAttachment: Attachment = Attachment(name, hashes, size, contentType, id)
}

object AttachmentInputValue {

  def apply(attachment: Attachment) =
    new AttachmentInputValue(attachment.name, attachment.hashes, attachment.size, attachment.contentType, attachment.id)
}

/**
  * Define a data value from HTTP request as null (empty value)
  */
object NullInputValue extends InputValue {
  def jsonValue: JsValue = JsNull
}

/**
  * Contain data values from HTTP request
  */
class Fields(private val fields: Map[String, InputValue]) {

  /**
    * Get InputValue
    */
  def get(name: String): Option[InputValue] =
    fields.get(name)

  /**
    * Get data value as String. Returns None if field doesn't exist or format is not a string
    */
  def getString(name: String): Option[String] =
    fields.get(name) collect {
      case StringInputValue(Seq(s))    => s
      case JsonInputValue(JsString(s)) => s
    }

  /**
    * Get data value as list of String. Returns None if field doesn't exist or format is not a list of string
    */
  def getStrings(name: String): Option[Seq[String]] = fields.get(name) flatMap {
    case StringInputValue(ss)        => Some(ss)
    case JsonInputValue(js: JsArray) => js.asOpt[Seq[String]]
    case _                           => None
  }

  /**
    * Get data value as list of String. Returns None if field doesn't exist or format is not a list of string
    */
  def getStrings(name: String, separator: String): Option[Seq[String]] = fields.get(name) flatMap {
    case StringInputValue(ss)        => Some(ss.flatMap(_.split(separator)).filterNot(_.isEmpty))
    case JsonInputValue(js: JsArray) => js.asOpt[Seq[String]]
    case _                           => None
  }

  /**
    * Get data value as Long. Returns None if field doesn't exist or format is not a Long
    */
  def getLong(name: String): Option[Long] = fields.get(name) flatMap {
    case StringInputValue(Seq(s))    => Try(s.toLong).toOption
    case JsonInputValue(JsNumber(b)) => Some(b.longValue)
    case _                           => None
  }

  def getBoolean(name: String): Option[Boolean] = fields.get(name) flatMap {
    case JsonInputValue(JsBoolean(b)) => Some(b)
    case StringInputValue(Seq(s))     => Try(s.toBoolean).orElse(Try(s.toLong == 1)).toOption
    case _                            => None
  }

  /**
    * Get data value as json. Returns None if field doesn't exist or can't be converted to json
    */
  def getValue(name: String): Option[JsValue] = fields.get(name) collect {
    case JsonInputValue(js)       => js
    case StringInputValue(Seq(s)) => JsString(s)
    case StringInputValue(ss)     => Json.toJson(ss)
  }

  def getValues(name: String): Seq[JsValue] = fields.get(name).toSeq flatMap {
    case JsonInputValue(JsArray(js)) => js
    case StringInputValue(ss)        => ss.map(s => JsString(s))
    case _                           => Nil
  }

  /**
    * Extract all fields, name and value
    */
  def map[A](f: ((String, InputValue)) => A): immutable.Iterable[A] = fields.map(f)

  /**
    * Extract all field values
    */
  def mapValues(f: InputValue => InputValue) = new Fields(fields.view.mapValues(f).toMap)

  /**
    * Returns a copy of this class with a new field (or replacing existing field)
    */
  def set(name: String, value: InputValue): Fields = new Fields(fields + (name -> value))

  /**
    * Returns a copy of this class with a new field (or replacing existing field)
    */
  def set(name: String, value: String): Fields = set(name, StringInputValue(Seq(value)))

  /**
    * Returns a copy of this class with a new field (or replacing existing field)
    */
  def set(name: String, value: JsValue): Fields = set(name, JsonInputValue(value))

  /**
    * Returns a copy of this class with a new field if value is not None otherwise returns this
    */
  def set(name: String, value: Option[JsValue]): Fields = value.fold(this)(v => set(name, v))

  /**
    * Return a copy of this class without the specified field
    */
  def unset(name: String): Fields = new Fields(fields - name)

  /**
    * Returns true if the specified field name is present
    */
  def contains(name: String): Boolean = fields.contains(name)

  def isEmpty: Boolean = fields.isEmpty

  def addIfAbsent(name: String, value: String): Fields = getString(name).fold(set(name, value))(_ => this)

  def addIfAbsent(name: String, value: JsValue): Fields = getValue(name).fold(set(name, value))(_ => this)

  def addIfAbsent(name: String, value: InputValue): Fields = get(name).fold(set(name, value))(_ => this)

  def ++(other: IterableOnce[(String, InputValue)]) = new Fields(fields ++ other)

  override def toString: String = fields.toString()
}

object Fields {
  val empty: Fields = new Fields(Map.empty[String, InputValue])

  /**
    * Create an instance of Fields from a JSON object
    */
  def apply(obj: JsObject): Fields = {
    val fields = obj.value.view.mapValues(v => JsonInputValue(v)).toMap
    new Fields(fields)
  }

  def apply(fields: Map[String, InputValue]): Fields = {
    if (fields.keysIterator.exists(_.startsWith("_")))
      throw BadRequestError("Field starting with '_' is forbidden")
    new Fields(fields)
  }
}

class FieldsBodyParser @Inject() (playBodyParsers: PlayBodyParsers, implicit val ec: ExecutionContext) extends BodyParser[Fields] {

  private[FieldsBodyParser] lazy val logger = Logger(getClass)

  def apply(request: RequestHeader): Accumulator[ByteString, Either[Result, Fields]] = {
    def queryFields = request.queryString.view.mapValues(v => StringInputValue(v)).toMap

    request.contentType.map(_.toLowerCase(Locale.ENGLISH)) match {

      case Some("text/json") | Some("application/json") => playBodyParsers.json[Fields].map(f => f ++ queryFields).apply(request)

      case Some("application/x-www-form-urlencoded") =>
        playBodyParsers
          .tolerantFormUrlEncoded
          .map { form =>
            Fields(form.view.mapValues(v => StringInputValue(v)).toMap)
          }
          .map(f => f ++ queryFields)
          .apply(request)

      case Some("multipart/form-data") =>
        playBodyParsers
          .multipartFormData
          .map {
            case MultipartFormData(dataParts, files, _) =>
              val dataFields = dataParts
                .getOrElse("_json", Nil)
                .headOption
                .map { s =>
                  Json
                    .parse(s)
                    .as[JsObject]
                    .value
                    .view
                    .mapValues(v => JsonInputValue(v))
                    .toMap
                }
                .getOrElse(Map.empty)
              val fileFields = files.map { f =>
                f.key -> FileInputValue(f.filename.split("[/\\\\]").last, f.ref.path, f.contentType.getOrElse("application/octet-stream"))
              }
              Fields(dataFields ++ fileFields ++ queryFields)
          }
          .apply(request)

      case contentType =>
        val contentLength = request.headers.get("Content-Length").fold(0)(_.toInt)
        if (contentLength != 0)
          logger.warn(s"Unrecognized content-type : ${contentType.getOrElse("not set")} on $request (length=$contentLength)")
        Accumulator.done(Right(Fields(queryFields)))
    }
  }
}
