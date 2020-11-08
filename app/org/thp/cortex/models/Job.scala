package org.thp.cortex.models

import scala.util.Try

import play.api.libs.json.{JsObject, JsString, Json}

import javax.inject.{Inject, Singleton}
import org.thp.cortex.models.JsonFormat.workerTypeFormat

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat => F, AttributeOption => O}

object JobStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Waiting, InProgress, Success, Failure, Deleted = Value
  implicit val reads                                 = enumFormat(this)
}

trait JobAttributes {
  _: AttributeDef =>
  val workerDefinitionId = attribute("workerDefinitionId", F.stringFmt, "Worker definition id", O.readonly)
  val workerId           = attribute("workerId", F.stringFmt, "Worker id", O.readonly)
  val workerName         = attribute("workerName", F.stringFmt, "Worker name", O.readonly)
  val organization       = attribute("organization", F.stringFmt, "Organization ID", O.readonly)
  val status             = attribute("status", F.enumFmt(JobStatus), "Status of the job")
  val startDate          = optionalAttribute("startDate", F.dateFmt, "Analysis start date")
  val endDate            = optionalAttribute("endDate", F.dateFmt, "Analysis end date")
  val dataType           = attribute("dataType", F.stringFmt, "Type of the artifact", O.readonly)
  val data               = optionalAttribute("data", F.rawFmt, "Content of the artifact", O.readonly)
  val attachment         = optionalAttribute("attachment", F.attachmentFmt, "Artifact file content", O.readonly)
  val tlp                = attribute("tlp", TlpAttributeFormat, "TLP level", 2L)
  val pap                = attribute("pap", TlpAttributeFormat, "PAP level", 2L)
  val message            = optionalAttribute("message", F.textFmt, "Message associated to the analysis")
  val errorMessage       = optionalAttribute("errorMessage", F.textFmt, "Message returned by the worker when it fails")
  val parameters         = attribute("parameters", F.rawFmt, "Parameters for this job", "{}")
  val input              = optionalAttribute("input", F.rawFmt, "Data sent to worker")
  val fromCache          = optionalAttribute("fromCache", F.booleanFmt, "Indicates if cache is used", O.form)
  val tpe                = attribute("type", F.enumFmt(WorkerType), "", O.readonly)
  val lbel               = optionalAttribute("label", F.stringFmt, "Label of the job")
  val cacheTag           = optionalAttribute("cacheTag", F.stringFmt, "hash of job discriminant, used for cache", O.readonly)
}

@Singleton
class JobModel @Inject() () extends ModelDef[JobModel, Job]("job", "Job", "/job") with JobAttributes with AuditedModel {

  override val removeAttribute: JsObject = Json.obj("status" -> JobStatus.Deleted)

  override def defaultSortBy: Seq[String] = Seq("-createdAt")
}

class Job(model: JobModel, attributes: JsObject) extends EntityDef[JobModel, Job](model, attributes) with JobAttributes {
  val params: JsObject = Try(Json.parse(parameters()).as[JsObject]).getOrElse(JsObject.empty)

  override def toJson: JsObject = {
    val output = input().fold(super.toJson)(i =>
      super.toJson +
        ("input" -> Json.parse(i))
    ) +
      ("parameters"           -> params) +
      ("analyzerId"           -> JsString(workerId())) +
      ("analyzerName"         -> JsString(workerName())) +
      ("analyzerDefinitionId" -> JsString(workerDefinitionId())) +
      ("date"                 -> Json.toJson(createdAt))
    data() match {
      case Some(d) if tpe() == WorkerType.responder => output + ("data" -> Json.parse(d))
      case _                                        => output
    }
  }
}
