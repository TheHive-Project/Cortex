package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.util.Try

import play.api.libs.json.{ JsObject, Json }

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{ AttributeDef, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat ⇒ F, AttributeOption ⇒ O }

object RateUnit extends Enumeration with HiveEnumeration {
  type Type = Value
  val Day = Value(24 * 60 * 60)
  val Month = Value(30 * 24 * 60 * 60)
  implicit val reads = enumFormat(this)
}

trait AnalyzerAttributes { _: AttributeDef ⇒
  val name = attribute("name", F.stringFmt, "Analyzer name")
  val analyzerDefinitionId = attribute("analyzerDefinitionId", F.stringFmt, "Analyzer definition id", O.readonly)
  val dataTypeList = multiAttribute("dataTypeList", F.stringFmt, "List of data type this analyzer can manage")
  val configuration = attribute("configuration", F.textFmt, "Configuration of analyzer", O.sensitive)
  val rate = optionalAttribute("rate", F.numberFmt, "Number ")
  val rateUnit = optionalAttribute("rateUnit", F.enumFmt(RateUnit), "")
}

@Singleton
class AnalyzerModel @Inject() (
    organizationModel: OrganizationModel) extends ChildModelDef[AnalyzerModel, Analyzer, OrganizationModel, Organization](organizationModel, "analyzer", "Analyzer", "/analyzer") with AnalyzerAttributes with AuditedModel {
}

class Analyzer(model: AnalyzerModel, attributes: JsObject) extends EntityDef[AnalyzerModel, Analyzer](model, attributes) with AnalyzerAttributes {
  def config: JsObject = Try(Json.parse(configuration()).as[JsObject]).getOrElse(JsObject.empty)
}
