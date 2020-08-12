package org.thp.cortex.models

import javax.inject.{Inject, Singleton}

import play.api.libs.json.JsObject

import org.elastic4play.models.{AttributeDef, EntityDef, AttributeFormat => F, AttributeOption => O, ChildModelDef}

trait ReportAttributes { _: AttributeDef =>
  val full       = attribute("full", F.rawFmt, "Full content of the report", O.readonly)
  val summary    = attribute("summary", F.rawFmt, "Summary of the report", O.readonly)
  val operations = attribute("operations", F.rawFmt, "Update operations applied at the end of the job", "[]", O.unaudited)
}

@Singleton
class ReportModel @Inject() (jobModel: JobModel)
    extends ChildModelDef[ReportModel, Report, JobModel, Job](jobModel, "report", "Report", "/report")
    with ReportAttributes {}

class Report(model: ReportModel, attributes: JsObject) extends EntityDef[ReportModel, Report](model, attributes) with ReportAttributes
