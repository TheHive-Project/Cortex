package org.elastic4play.models

import scala.util.Try

import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.{JsObject, JsString, Json}

case class InvalidEntityAttributes[M <: BaseModelDef, T](model: M, name: String, format: AttributeFormat[T], attributes: JsObject)
    extends Exception(
      s"Entity is not conform to its model ${model.modelName} : missing attribute $name of type ${format.name}\n" +
        s"${Json.prettyPrint(attributes)}\n =⇒ ${Try(format.jsFormat.reads((attributes \ name).as[JsString]))}"
    )
