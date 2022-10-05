package org.thp.cortex.models

import java.nio.file.{Path, Paths}

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import org.scalactic.Accumulation._
import org.scalactic._

import org.elastic4play.controllers.JsonInputValue
import org.elastic4play.models.HiveEnumeration
import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.{AttributeError, InvalidFormatAttributeError, MissingAttributeError}

object WorkerConfigItemType extends Enumeration with HiveEnumeration {
  type Type = Value
  val text, string, number, boolean                     = Value
  implicit val reads: Format[WorkerConfigItemType.Type] = enumFormat(this)
}

case class ConfigurationDefinitionItem(
    name: String,
    description: String,
    `type`: WorkerConfigItemType.Type,
    multi: Boolean,
    required: Boolean,
    defaultValue: Option[JsValue]
) {
  def isRequired: Boolean = required

  def isMulti: Boolean = multi

  private def check(v: JsValue): JsValue Or Every[AttributeError] = {
    import WorkerConfigItemType._
    v match {
      case _: JsString if `type` == string || `type` == text => Good(v)
      case _: JsNumber if `type` == number                   => Good(v)
      case _: JsBoolean if `type` == boolean                 => Good(v)
      case JsNull if !isRequired                             => Good(v)
      case _                                                 => Bad(One(InvalidFormatAttributeError(s"$name[]", `type`.toString, JsonInputValue(v))))
    }
  }

  def read(config: JsObject): (String, JsValue) Or Every[AttributeError] =
    (config \ name)
      .toOption
      .orElse(defaultValue)
      .map {
        case JsArray(values) if isMulti => values.validatedBy(check).map(a => name -> JsArray(a))
        case value if !isMulti          => check(value).map(name -> _)
        case value                      => Bad(One(InvalidFormatAttributeError(name, `type`.toString, JsonInputValue(value))))
      }
      .getOrElse {
        if (isMulti) Good(name -> JsArray.empty)
        else if (isRequired) Bad(One(MissingAttributeError(name)))
        else Good(name -> JsNull)
      }
}

object ConfigurationDefinitionItem {
  implicit val reads: Reads[ConfigurationDefinitionItem] = ((JsPath \ "name").read[String] and
    (JsPath \ "description").read[String] and
    (JsPath \ "type").read[WorkerConfigItemType.Type] and
    (JsPath \ "multi").readWithDefault[Boolean](false) and
    (JsPath \ "required").readWithDefault[Boolean](false) and
    (JsPath \ "defaultValue").readNullable[JsValue])(ConfigurationDefinitionItem.apply _)
  implicit val writes: Writes[ConfigurationDefinitionItem] = Json.writes[ConfigurationDefinitionItem]
}

case class WorkerDefinition(
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    author: String,
    url: String,
    license: String,
    dockerImage: Option[String],
    command: Option[Path],
    baseConfiguration: Option[String],
    configurationItems: Seq[ConfigurationDefinitionItem],
    configuration: JsObject,
    tpe: WorkerType.Type
) {
  val id: String = (name + "_" + version).replaceAll("\\.", "_")

  def canProcessDataType(dataType: String): Boolean = dataTypeList.contains(dataType)
}

object WorkerDefinition {
  lazy val logger = Logger(getClass)

  def singleReads(workerType: WorkerType.Type): Reads[WorkerDefinition] =
    ((JsPath \ "name").read[String] and
      (JsPath \ "version").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "dataTypeList").read[Seq[String]].orElse(Reads.pure(Nil)) and
      (JsPath \ "author").read[String] and
      (JsPath \ "url").read[String] and
      (JsPath \ "license").read[String] and
      (JsPath \ "dockerImage").readNullable[String] and
      (JsPath \ "command").readNullable[String].map(_.map(Paths.get(_))) and
      (JsPath \ "baseConfig").readNullable[String] and
      (JsPath \ "configurationItems").read[Seq[ConfigurationDefinitionItem]].orElse(Reads.pure(Nil)) and
      (JsPath \ "config").read[JsObject].orElse(Reads.pure(JsObject.empty)) and
      Reads.pure(workerType))(WorkerDefinition.apply _)

  def reads(workerType: WorkerType.Type): Reads[List[WorkerDefinition]] = {
    val reads = singleReads(workerType)
    implicitly[Reads[JsArray]]
    reads.map(List(_)) orElse JsArrayReads.map(array =>
      array
        .value
        .toList
        .flatMap { js =>
          reads
            .reads(js)
            .fold(
              invalid => {
                logger.warn(s"The catalog contains an invalid entry\n  entry:$js\n  $invalid")
                Seq.empty
              },
              Seq(_)
            )
        }
    )
  }

  implicit val writes: Writes[WorkerDefinition] = Writes[WorkerDefinition] { workerDefinition =>
    Json.obj(
      "id"                 -> workerDefinition.id,
      "name"               -> workerDefinition.name,
      "version"            -> workerDefinition.version,
      "description"        -> workerDefinition.description,
      "dataTypeList"       -> workerDefinition.dataTypeList,
      "author"             -> workerDefinition.author,
      "url"                -> workerDefinition.url,
      "license"            -> workerDefinition.license,
      "baseConfig"         -> workerDefinition.baseConfiguration,
      "configurationItems" -> workerDefinition.configurationItems,
      "dockerImage"        -> workerDefinition.dockerImage,
      "command"            -> workerDefinition.command.map(_.getFileName.toString)
    )
  }
}
