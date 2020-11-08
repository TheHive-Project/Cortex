package org.thp.cortex.models

import scala.concurrent.duration.Duration

import play.api.Configuration
import play.api.libs.json._

import org.elastic4play.utils.Collection.distinctBy

case class BaseConfig(name: String, workerNames: Seq[String], items: Seq[ConfigurationDefinitionItem], config: Option[WorkerConfig]) {
  def +(other: BaseConfig) = BaseConfig(name, workerNames ++ other.workerNames, distinctBy(items ++ other.items)(_.name), config.orElse(other.config))
}

object BaseConfig {
  implicit val writes: Writes[BaseConfig] = Writes[BaseConfig] { baseConfig =>
    Json.obj(
      "name"               -> baseConfig.name,
      "workers"            -> baseConfig.workerNames,
      "configurationItems" -> baseConfig.items,
      "config"             -> baseConfig.config.fold(JsObject.empty)(_.jsonConfig)
    )
  }

  def global(tpe: WorkerType.Type, configuration: Configuration): BaseConfig = {
    val typedItems = tpe match {
      case WorkerType.responder => Nil
      case WorkerType.analyzer =>
        Seq(
          ConfigurationDefinitionItem(
            "auto_extract_artifacts",
            "extract artifacts from full report automatically",
            WorkerConfigItemType.boolean,
            multi = false,
            required = false,
            Some(JsFalse)
          ),
          ConfigurationDefinitionItem(
            "jobCache",
            "maximum time, in minutes, previous result is used if similar job is requested",
            WorkerConfigItemType.number,
            multi = false,
            required = false,
            configuration.getOptional[Duration]("cache.job").map(d => JsNumber(d.toMinutes))
          )
        )
    }
    BaseConfig(
      "global",
      Nil,
      typedItems ++ Seq(
        ConfigurationDefinitionItem("proxy_http", "url of http proxy", WorkerConfigItemType.string, multi = false, required = false, None),
        ConfigurationDefinitionItem("proxy_https", "url of https proxy", WorkerConfigItemType.string, multi = false, required = false, None),
        ConfigurationDefinitionItem("cacerts", "certificate authorities", WorkerConfigItemType.text, multi = false, required = false, None),
        ConfigurationDefinitionItem(
          "jobTimeout",
          "maximum allowed job execution time (in minutes)",
          WorkerConfigItemType.number,
          multi = false,
          required = false,
          configuration.getOptional[Duration]("job.timeout").map(d => JsNumber(d.toMinutes))
        )
      ),
      None
    )
  }

  val tlp = BaseConfig(
    "tlp",
    Nil,
    Seq(
      ConfigurationDefinitionItem("check_tlp", "", WorkerConfigItemType.boolean, multi = false, required = false, None),
      ConfigurationDefinitionItem("max_tlp", "", WorkerConfigItemType.number, multi = false, required = false, None)
    ),
    None
  )

  val pap = BaseConfig(
    "pap",
    Nil,
    Seq(
      ConfigurationDefinitionItem("check_pap", "", WorkerConfigItemType.boolean, multi = false, required = false, None),
      ConfigurationDefinitionItem("max_pap", "", WorkerConfigItemType.number, multi = false, required = false, None)
    ),
    None
  )
}
