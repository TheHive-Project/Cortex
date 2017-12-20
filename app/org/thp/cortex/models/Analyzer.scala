package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.util.Try

import play.api.libs.json.{ JsObject, Json }

import org.elastic4play.models.{ AttributeDef, ChildModelDef, EntityDef, AttributeFormat ⇒ F, AttributeOption ⇒ O }

trait AnalyzerAttributes { _: AttributeDef ⇒
  val name = attribute("name", F.stringFmt, "Analyzer name")
  val analyzerDefinitionId = attribute("analyzerDefinitionId", F.stringFmt, "Analyzer definition id", O.readonly)
  val analyzerDefinitionName = attribute("analyzerDefinitionName", F.stringFmt, "Analyzer definition name", O.readonly)
  val configuration = attribute("configuration", F.textFmt, "Configuration of analyzer")
}

@Singleton
class AnalyzerModel @Inject() (
    organizationModel: OrganizationModel) extends ChildModelDef[AnalyzerModel, Analyzer, OrganizationModel, Organization](organizationModel, "analyzer", "Analyzer", "/analyzer") with AnalyzerAttributes with AuditedModel {
}

class Analyzer(model: AnalyzerModel, attributes: JsObject) extends EntityDef[AnalyzerModel, Analyzer](model, attributes) with AnalyzerAttributes {
  def config: JsObject = Try(Json.parse(configuration()).as[JsObject]).getOrElse(JsObject.empty)
}
