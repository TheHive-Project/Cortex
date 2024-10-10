package org.elastic4play.models

import com.sksamuel.elastic4s.fields.{ElasticField, TextField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicTemplateRequest
import org.elastic4play.controllers.InputValue
import org.elastic4play.services.{Attachment, DBLists}
import org.elastic4play.{AttributeError, InvalidFormatAttributeError, MissingAttributeError, UpdateReadOnlyAttributeError}
import org.scalactic._
import play.api.Logger
import play.api.libs.json.{Format, JsArray, JsNull, JsValue}

case class AttributeDefinition(name: String, `type`: String, description: String, values: Seq[JsValue], labels: Seq[String])

abstract class AttributeFormat[T](val name: String)(implicit val jsFormat: Format[T]) {
  def checkJson(subNames: Seq[String], value: JsValue): JsValue Or Every[AttributeError]

  def checkJsonForCreation(subNames: Seq[String], value: JsValue): JsValue Or Every[AttributeError] =
    checkJson(subNames, value)

  def checkJsonForUpdate(subNames: Seq[String], value: JsValue): JsValue Or Every[AttributeError] =
    checkJson(subNames, value)

  def inputValueToJson(subNames: Seq[String], value: InputValue): JsValue Or Every[AttributeError] =
    fromInputValue(subNames, value).map(v => jsFormat.writes(v))

  def fromInputValue(subNames: Seq[String], value: InputValue): T Or Every[AttributeError]

  def elasticType(attributeName: String): ElasticField

  def elasticTemplate(attributePath: Seq[String]): Seq[DynamicTemplateRequest] = Nil

  protected def formatError(value: InputValue): Bad[One[InvalidFormatAttributeError]] = Bad(One(InvalidFormatAttributeError("", name, value)))

  def definition(dblists: DBLists, attribute: Attribute[T]): Seq[AttributeDefinition] =
    Seq(AttributeDefinition(attribute.attributeName, name, attribute.description, Nil, Nil))
}

object AttributeFormat {
  val dateFmt: DateAttributeFormat               = DateAttributeFormat
  val textFmt: TextAttributeFormat               = TextAttributeFormat
  val stringFmt: StringAttributeFormat           = StringAttributeFormat
  val userFmt: UserAttributeFormat               = UserAttributeFormat
  val booleanFmt: BooleanAttributeFormat         = BooleanAttributeFormat
  val numberFmt: NumberAttributeFormat           = NumberAttributeFormat
  val attachmentFmt: AttributeFormat[Attachment] = AttachmentAttributeFormat
  val metricsFmt: MetricsAttributeFormat         = MetricsAttributeFormat
  val customFields: CustomAttributeFormat        = CustomAttributeFormat
  val uuidFmt: UUIDAttributeFormat               = UUIDAttributeFormat
  val hashFmt: AttributeFormat[String]           = HashAttributeFormat
  val binaryFmt: BinaryAttributeFormat           = BinaryAttributeFormat
  val rawFmt: RawAttributeFormat                 = RawAttributeFormat

  def enumFmt[T <: Enumeration](e: T)(implicit format: Format[T#Value]): EnumerationAttributeFormat[T] = EnumerationAttributeFormat[T](e)

  def listEnumFmt(enumerationName: String)(dblists: DBLists): ListEnumerationAttributeFormat =
    ListEnumerationAttributeFormat(enumerationName)(dblists)

  def objectFmt(subAttributes: Seq[Attribute[_]]): ObjectAttributeFormat = ObjectAttributeFormat(subAttributes)
}

object AttributeOption extends Enumeration with HiveEnumeration {
  type Type = Value
  val readonly, unaudited, model, form, sensitive = Value
}

case class Attribute[T](
    modelName: String,
    attributeName: String,
    format: AttributeFormat[T],
    options: Seq[AttributeOption.Type],
    defaultValue: Option[() => T],
    description: String
) {

  private[Attribute] lazy val logger = Logger(getClass)

  def defaultValueJson: Option[JsValue] = defaultValue.map(d => format.jsFormat.writes(d()))

  lazy val isMulti: Boolean = format match {
    case _: MultiAttributeFormat[_] => true
    case _                          => false
  }
  lazy val isForm: Boolean      = !options.contains(AttributeOption.model)
  lazy val isModel: Boolean     = !options.contains(AttributeOption.form)
  lazy val isReadonly: Boolean  = options.contains(AttributeOption.readonly)
  lazy val isUnaudited: Boolean = options.contains(AttributeOption.unaudited) || isSensitive || isReadonly
  lazy val isSensitive: Boolean = options.contains(AttributeOption.sensitive)
  lazy val isRequired: Boolean = format match {
    case _: OptionalAttributeFormat[_] => false
    case _: MultiAttributeFormat[_]    => false
    case _                             => true
  }

  def elasticMapping: ElasticField = format.elasticType(attributeName) match {
    case a: TextField if isSensitive && a.`type` == "String" => a.index(false)
    case a                                                   => a
  }

  def elasticTemplate(attributePath: Seq[String] = Nil): Seq[DynamicTemplateRequest] =
    format.elasticTemplate(attributePath :+ attributeName)

  def validateForCreation(value: Option[JsValue]): Option[JsValue] Or Every[AttributeError] = {
    val result = value match {
      case Some(JsNull) if !isRequired         => Good(value)
      case Some(JsArray(Seq())) if !isRequired => Good(value)
      case None if !isRequired                 => Good(value)
      case Some(JsNull) | Some(JsArray(Seq())) | None =>
        if (defaultValueJson.isDefined)
          Good(defaultValueJson)
        else
          Bad(One(MissingAttributeError(attributeName)))
      case Some(v) =>
        format
          .checkJsonForCreation(Nil, v)
          .transform(
            g => Good(Some(g)),
            x =>
              Bad(x.map {
                case ifae: InvalidFormatAttributeError => ifae.copy(name = attributeName)
                case other                             => other
              })
          )
    }
    logger.debug(s"$modelName.$attributeName(${format.name}).validateForCreation($value) ⇒ $result")
    result
  }

  def validateForUpdate(subNames: Seq[String], value: JsValue): JsValue Or Every[AttributeError] = {
    val result = value match {
      case _ if isReadonly                       => Bad(One(UpdateReadOnlyAttributeError(attributeName)))
      case JsNull | JsArray(Seq()) if isRequired => Bad(One(MissingAttributeError(attributeName)))
      case JsNull | JsArray(Seq())               => Good(value)
      case v =>
        format
          .checkJsonForUpdate(subNames, v)
          .badMap(_.map {
            case ifae: InvalidFormatAttributeError => ifae.copy(name = attributeName)
            case other                             => other
          })
    }
    logger.debug(s"$modelName.$attributeName(${format.name}).validateForUpdate($value) ⇒ $result")
    result
  }
}
