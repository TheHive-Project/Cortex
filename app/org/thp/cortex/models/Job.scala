package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.util.Try

import play.api.libs.json.{ JsObject, Json }

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{ AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F, AttributeOption ⇒ O }

object JobStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Waiting, InProgress, Success, Failure, Deleted = Value
  implicit val reads = enumFormat(this)
}

trait JobAttributes {
  _: AttributeDef ⇒
  val workerDefinitionId = attribute("workerDefinitionId", F.stringFmt, "Worker definition id", O.readonly)
  val workerId = attribute("workerId", F.stringFmt, "Worker id", O.readonly)
  val workerName = attribute("workerName", F.stringFmt, "Worker name", O.readonly)
  val organization = attribute("organization", F.stringFmt, "Organization ID", O.readonly)
  val status = attribute("status", F.enumFmt(JobStatus), "Status of the job")
  val startDate = optionalAttribute("startDate", F.dateFmt, "Analysis start date")
  val endDate = optionalAttribute("endDate", F.dateFmt, "Analysis end date")
  val dataType = attribute("dataType", F.stringFmt, "Type of the artifact", O.readonly)
  val data = optionalAttribute("data", F.stringFmt, "Content of the artifact", O.readonly)
  val attachment = optionalAttribute("attachment", F.attachmentFmt, "Artifact file content", O.readonly)
  val tlp = attribute("tlp", TlpAttributeFormat, "TLP level", 2L)
  val message = optionalAttribute("message", F.textFmt, "Message associated to the analysis")
  val errorMessage = optionalAttribute("message", F.textFmt, "Message returned by the worker when it fails")
  val parameters = attribute("parameters", F.stringFmt, "Parameters for this job", "{}")
  val input = optionalAttribute("input", F.textFmt, "Data sent to worker")
  val fromCache = optionalAttribute("fromCache", F.booleanFmt, "Indicates if cache is used", O.form)
}

@Singleton
class JobModel @Inject() () extends ModelDef[JobModel, Job]("job", "Job", "/job") with JobAttributes with AuditedModel {

  override val removeAttribute: JsObject = Json.obj("status" -> JobStatus.Deleted)

  override def defaultSortBy: Seq[String] = Seq("-createdAt")
}

class Job(model: JobModel, attributes: JsObject) extends EntityDef[JobModel, Job](model, attributes) with JobAttributes {
  val params: JsObject = Try(Json.parse(parameters()).as[JsObject]).getOrElse(JsObject.empty)

  override def toJson: JsObject = {
    val output = super.toJson + ("date" -> Json.toJson(createdAt))
    input().fold(output)(i ⇒ output +
      ("input" -> Json.parse(i))) +
      ("parameters" -> params)
  }
}