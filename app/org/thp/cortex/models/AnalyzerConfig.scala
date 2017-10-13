package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.util.Try

import play.api.libs.json.{ JsObject, Json }

import org.thp.cortex.services.AuditedModel

import org.elastic4play.models.{ AttributeDef, ChildModelDef, EntityDef, AttributeFormat ⇒ F, AttributeOption ⇒ O }

trait AnalyzerConfigAttributes { _: AttributeDef ⇒
  val analyzerId = attribute("analyzerId", F.stringFmt, "Analyzer id", O.readonly)
  val configuration = attribute("configuration", F.textFmt, "Configuration of analyzer")
}

@Singleton
class AnalyzerConfigModel @Inject() (
    subscriptionModel: SubscriptionModel) extends ChildModelDef[AnalyzerConfigModel, AnalyzerConfig, SubscriptionModel, Subscription](subscriptionModel, "analyzerConfig", "AnalyzerConfig", "/analyzerConfig") with AnalyzerConfigAttributes with AuditedModel {
}

class AnalyzerConfig(model: AnalyzerConfigModel, attributes: JsObject) extends EntityDef[AnalyzerConfigModel, AnalyzerConfig](model, attributes) with AnalyzerConfigAttributes {
  def config: JsObject = Try(Json.parse(configuration()).as[JsObject]).getOrElse(JsObject.empty)
}