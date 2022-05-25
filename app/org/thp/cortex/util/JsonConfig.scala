package org.thp.cortex.util

import com.typesafe.config.ConfigValueType.{BOOLEAN, NULL, NUMBER, STRING}
import com.typesafe.config.{ConfigList, ConfigObject, ConfigValue}
import play.api.Configuration
import play.api.libs.json._

import scala.collection.JavaConverters._

object JsonConfig {
  implicit val configValueWrites: Writes[ConfigValue] = Writes((value: ConfigValue) =>
    value match {
      case v: ConfigObject             => configWrites.writes(Configuration(v.toConfig))
      case v: ConfigList               => JsArray(v.asScala.map(x => configValueWrites.writes(x)))
      case v if v.valueType == NUMBER  => JsNumber(BigDecimal(v.unwrapped.asInstanceOf[Number].toString))
      case v if v.valueType == BOOLEAN => JsBoolean(v.unwrapped.asInstanceOf[Boolean])
      case v if v.valueType == NULL    => JsNull
      case v if v.valueType == STRING  => JsString(v.unwrapped.asInstanceOf[String])
    }
  )

  implicit def configWrites: OWrites[Configuration] = OWrites { (cfg: Configuration) =>
    JsObject(cfg.subKeys.map(key => key -> configValueWrites.writes(cfg.underlying.getValue(key))).toSeq)
  }
}
