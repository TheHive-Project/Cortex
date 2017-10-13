package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.{ AttributeDef, EntityDef, AttributeFormat ⇒ F, AttributeOption ⇒ O, ChildModelDef }

trait ReportAttributes { _: AttributeDef ⇒
  val full = attribute("data", F.textFmt, "Full content of the report", O.readonly)
  val summary = attribute("data", F.textFmt, "Summary of the report", O.readonly)
}

@Singleton
class ReportModel @Inject() (
    jobModel: JobModel) extends ChildModelDef[ReportModel, Report, JobModel, Job](jobModel, "report", "Report", "/report") with ReportAttributes {

}

class Report(model: ReportModel, attributes: JsObject) extends EntityDef[ReportModel, Report](model, attributes) with ReportAttributes
