package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future
import scala.util.Try

import play.api.libs.json.{ JsObject, JsString, Json }

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{ AttributeDef, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat ⇒ F, AttributeOption ⇒ O }
import org.elastic4play.utils.Hasher

import org.thp.cortex.models.JsonFormat.workerTypeFormat

object RateUnit extends Enumeration with HiveEnumeration {
  type Type = Value
  val Day = Value(1)
  val Month = Value(30)
  implicit val reads = enumFormat(this)
}

object WorkerType extends Enumeration with HiveEnumeration {
  type Type = Value
  val analyzer, responder = Value
}

trait WorkerAttributes { _: AttributeDef ⇒
  val workerId = attribute("_id", F.stringFmt, "Worker id", O.model)
  val name = attribute("name", F.stringFmt, "Worker name")
  val workerDefinitionId = attribute("workerDefinitionId", F.stringFmt, "Worker definition id", O.readonly)
  val description = attribute("description", F.textFmt, "Worker description")
  val dataTypeList = multiAttribute("dataTypeList", F.stringFmt, "List of data type this worker can manage")
  val configuration = attribute("configuration", F.textFmt, "Configuration of the worker", O.sensitive)
  val rate = optionalAttribute("rate", F.numberFmt, "Number ")
  val rateUnit = optionalAttribute("rateUnit", F.enumFmt(RateUnit), "")
  val jobCache = optionalAttribute("jobCache", F.numberFmt, "")
  val tpe = attribute("type", F.enumFmt(WorkerType), "", O.readonly)
}

@Singleton
class WorkerModel @Inject() (organizationModel: OrganizationModel) extends ChildModelDef[WorkerModel, Worker, OrganizationModel, Organization](organizationModel, "worker", "Worker", "/worker") with WorkerAttributes with AuditedModel {
  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    val hasher = Hasher("md5")
    val id = for {
      organizationId ← parent.map(_.id)
      name ← (attrs \ "name").asOpt[String]
      tpe ← (attrs \ "type").asOpt[String]
    } yield hasher.fromString(s"${organizationId}_${name}_$tpe").head.toString
    Future.successful(attrs + ("_id" → JsString(id.getOrElse("<null>"))))
  }
}

class Worker(model: WorkerModel, attributes: JsObject) extends EntityDef[WorkerModel, Worker](model, attributes) with WorkerAttributes {
  def config: JsObject = Try(Json.parse(configuration()).as[JsObject]).getOrElse(JsObject.empty)
  def organization = parentId.get
}
