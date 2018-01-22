package org.thp.cortex.models

import java.nio.file.{ Path, Paths }

import scala.util.{ Failure, Success, Try }

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import org.scalactic.Accumulation._
import org.scalactic._

import org.elastic4play.controllers.JsonInputValue
import org.elastic4play.models.HiveEnumeration
import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.{ AttributeError, InvalidFormatAttributeError, MissingAttributeError }

//object AnalyzerStatus extends Enumeration with HiveEnumeration {
//  type Type = Value
//  val Enabled, Disabled = Value
//}

object AnalyzerConfigItemType extends Enumeration with HiveEnumeration {
  type Type = Value
  val string, number, boolean = Value
  implicit val reads = enumFormat(this)
}

case class ConfigurationDefinitionItem(
    name: String,
    description: String,
    `type`: AnalyzerConfigItemType.Type,
    multi: Boolean,
    required: Boolean,
    defaultValue: Option[JsValue]) {
  def isRequired: Boolean = required

  def isMulti: Boolean = multi

  private def check(v: JsValue): JsValue Or Every[AttributeError] = {
    import AnalyzerConfigItemType._
    v match {
      case _: JsString if `type` == string   ⇒ Good(v)
      case _: JsNumber if `type` == number   ⇒ Good(v)
      case _: JsBoolean if `type` == boolean ⇒ Good(v)
      case _                                 ⇒ Bad(One(InvalidFormatAttributeError(s"$name[]", `type`.toString, JsonInputValue(v))))
    }
  }

  def read(config: JsObject): (String, JsValue) Or Every[AttributeError] = {
    (config \ name).toOption
      .orElse(defaultValue)
      .map {
        case JsArray(values) if isMulti ⇒ values.validatedBy(check).map(a ⇒ name -> JsArray(a))
        case value if !isMulti          ⇒ check(value).map(name -> _)
        case value                      ⇒ Bad(One(InvalidFormatAttributeError(name, `type`.toString, JsonInputValue(value))))
      }
      .getOrElse {
        if (isMulti) Good(name -> JsArray.empty)
        else if (isRequired) Bad(One(MissingAttributeError(name)))
        else Good(name -> JsNull)
      }
  }
}

object ConfigurationDefinitionItem {
  implicit val reads: Reads[ConfigurationDefinitionItem] = (
    (JsPath \ "name").read[String] and
    (JsPath \ "description").read[String] and
    (JsPath \ "type").read[AnalyzerConfigItemType.Type] and
    (JsPath \ "multi").readWithDefault[Boolean](false) and
    (JsPath \ "required").readWithDefault[Boolean](false) and
    (JsPath \ "defaultValue").readNullable[JsValue])(ConfigurationDefinitionItem.apply _)
  implicit val writes: Writes[ConfigurationDefinitionItem] = Json.writes[ConfigurationDefinitionItem]
}

case class AnalyzerDefinition(
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    author: String,
    url: String,
    license: String,
    baseDirectory: Path,
    command: String,
    configurationItems: Seq[ConfigurationDefinitionItem],
    configuration: JsObject) {
  val id = (name + "_" + version).replaceAll("\\.", "_")

  def cmd: Path = Paths.get(command) //baseDirectory.resolve(command)

  def canProcessDataType(dataType: String): Boolean = dataTypeList.contains(dataType)
}

object AnalyzerDefinition {
  lazy val logger = Logger(getClass)

  def fromPath(definitionFile: Path): Try[AnalyzerDefinition] = {
    readJsonFile(definitionFile)
      .recoverWith {
        case error ⇒
          logger.warn(s"Load of analyzer $definitionFile fails", error)
          Failure(error)
      }
      .map(_.validate(AnalyzerDefinition.reads(definitionFile.getParent.getParent)))
      .flatMap {
        case JsSuccess(analyzerDefinition, _) ⇒ Success(analyzerDefinition)
        case JsError(errors)                  ⇒ sys.error(s"Json description file $definitionFile is invalid: $errors")
      }
  }

  private def readJsonFile(file: Path): Try[JsObject] = {
    val source = scala.io.Source.fromFile(file.toFile)
    val json = Try(Json.parse(source.mkString).as[JsObject])
    source.close()
    json
  }

  def reads(path: Path): Reads[AnalyzerDefinition] = (
    (JsPath \ "name").read[String] and
    (JsPath \ "version").read[String] and
    (JsPath \ "description").read[String] and
    (JsPath \ "dataTypeList").read[Seq[String]].orElse(Reads.pure(Nil)) and
    (JsPath \ "author").read[String] and
    (JsPath \ "url").read[String] and
    (JsPath \ "license").read[String] and
    Reads.pure(path) and
    (JsPath \ "command").read[String] and
    (JsPath \ "configurationItems").read[Seq[ConfigurationDefinitionItem]].orElse(Reads.pure(Nil)) and
    (JsPath \ "config").read[JsObject].orElse(Reads.pure(JsObject.empty)))(AnalyzerDefinition.apply _)
  implicit val writes: Writes[AnalyzerDefinition] = Writes[AnalyzerDefinition] { analyzerDefinition ⇒
    Json.obj(
      "id" -> analyzerDefinition.id,
      "name" -> analyzerDefinition.name,
      "version" -> analyzerDefinition.version,
      "description" -> analyzerDefinition.description,
      "dataTypeList" -> analyzerDefinition.dataTypeList,
      "author" -> analyzerDefinition.author,
      "url" -> analyzerDefinition.url,
      "license" -> analyzerDefinition.license,
      "configurationItems" -> analyzerDefinition.configurationItems)
  }
}