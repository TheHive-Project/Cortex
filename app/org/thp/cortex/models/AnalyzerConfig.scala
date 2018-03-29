package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.{ JsObject, Json }

import org.elastic4play.models.{ AttributeDef, ChildModelDef, EntityDef, AttributeFormat ⇒ F, AttributeOption ⇒ O }

trait AnalyzerConfigAttributes { _: AttributeDef ⇒
  val name = attribute("name", F.stringFmt, "Analyzer name")
  val config = attribute("config", F.textFmt, "Configuration of analyzer", O.sensitive)
}

@Singleton
class AnalyzerConfigModel @Inject() (
    organizationModel: OrganizationModel) extends ChildModelDef[AnalyzerConfigModel, AnalyzerConfig, OrganizationModel, Organization](organizationModel, "analyzerConfig", "AnalyzerConfig", "/analyzer/config") with AnalyzerConfigAttributes {
}

class AnalyzerConfig(model: AnalyzerConfigModel, attributes: JsObject) extends EntityDef[AnalyzerConfigModel, AnalyzerConfig](model, attributes) with AnalyzerConfigAttributes {
  def organization = parentId.get
  def jsonConfig = Json.parse(config()).as[JsObject]
}
