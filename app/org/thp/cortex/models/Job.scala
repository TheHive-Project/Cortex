package org.thp.cortex.models

import java.util.Date
import javax.inject.{ Inject, Singleton }

import scala.util.Try

import play.api.libs.json.{ JsObject, Json }

import org.thp.cortex.models.JsonFormat.jobStatusFormat

import org.elastic4play.models.{ AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F, AttributeOption ⇒ O }

object JobStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val InProgress, Success, Failure = Value
}

trait JobAttributes { _: AttributeDef ⇒
  val analyzerId = attribute("analyzerId", F.stringFmt, "Analyzer id", O.readonly)
  val status = attribute("status", F.enumFmt(JobStatus), "Status of the job", O.model)
  val startDate = attribute("startDate", F.dateFmt, "Analysis start date", new Date)
  val endDate = attribute("endDate", F.dateFmt, "Analysis end date", new Date)
  val dataType = attribute("dataType", F.stringFmt, "Type of the artifact", O.readonly)
  val data = optionalAttribute("data", F.stringFmt, "Content of the artifact", O.readonly)
  val attachment = optionalAttribute("attachment", F.attachmentFmt, "Artifact file content", O.readonly)
  val tlp = attribute("tlp", TlpAttributeFormat, "TLP level", 2L)
  val message = optionalAttribute("message", F.textFmt, "Message associated to the analysis")
  val errorMessage = optionalAttribute("message", F.textFmt, "Message returned by the analyzer when it fails")
  val parameters = attribute("parameters", F.textFmt, "Parameters for this job", "{}")
}

@Singleton
class JobModel @Inject() () extends ModelDef[JobModel, Job]("job", "Job", "/job") with JobAttributes {

}

class Job(model: JobModel, attributes: JsObject) extends EntityDef[JobModel, Job](model, attributes) with JobAttributes {
  val params: JsObject = Try(Json.parse(parameters()).as[JsObject]).getOrElse(JsObject.empty)

}