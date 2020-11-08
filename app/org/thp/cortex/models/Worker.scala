package org.thp.cortex.models

import javax.inject.{Inject, Singleton}
import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{AttributeDef, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat => F, AttributeOption => O}
import org.elastic4play.utils.Hasher
import org.thp.cortex.models.JsonFormat.workerTypeFormat
import play.api.libs.json.{JsObject, JsString, Json}

import scala.concurrent.Future
import scala.util.Try

object RateUnit extends Enumeration with HiveEnumeration {
  type Type = Value
  val Second         = Value(1)
  val Minute         = Value(60)
  val Hour           = Value(60 * 60)
  val Day            = Value(60 * 60 * 24)
  val Month          = Value(60 * 60 * 24 * 30)
  implicit val reads = enumFormat(this)
}

object WorkerType extends Enumeration with HiveEnumeration {
  type Type = Value
  val analyzer, responder = Value
}

trait WorkerAttributes { _: AttributeDef =>
  val workerId           = attribute("_id", F.stringFmt, "Worker id", O.model)
  val name               = attribute("name", F.stringFmt, "Worker name")
  val vers               = attribute("version", F.stringFmt, "Worker version", O.readonly)
  val workerDefinitionId = attribute("workerDefinitionId", F.stringFmt, "Worker definition id", O.readonly)
  val description        = attribute("description", F.textFmt, "Worker description", O.readonly)
  val author             = attribute("author", F.textFmt, "Worker author", O.readonly)
  val url                = attribute("url", F.textFmt, "Worker url", O.readonly)
  val license            = attribute("license", F.textFmt, "Worker license", O.readonly)
  val command            = optionalAttribute("command", F.textFmt, "Worker command", O.readonly)
  val dockerImage        = optionalAttribute("dockerImage", F.textFmt, "Worker docker image", O.readonly)
  val dataTypeList       = multiAttribute("dataTypeList", F.stringFmt, "List of data type this worker can manage")
  val configuration      = attribute("configuration", F.rawFmt, "Configuration of the worker", O.sensitive)
  val baseConfig         = attribute("baseConfig", F.stringFmt, "Base configuration key", O.readonly)
  val rate               = optionalAttribute("rate", F.numberFmt, "Number ")
  val rateUnit           = optionalAttribute("rateUnit", F.enumFmt(RateUnit), "")
  val jobCache           = optionalAttribute("jobCache", F.numberFmt, "")
  val jobTimeout         = optionalAttribute("jobTimeout", F.numberFmt, "")
  val tpe                = attribute("type", F.enumFmt(WorkerType), "", O.readonly)
}

@Singleton
class WorkerModel @Inject() (organizationModel: OrganizationModel)
    extends ChildModelDef[WorkerModel, Worker, OrganizationModel, Organization](organizationModel, "worker", "Worker", "/worker")
    with WorkerAttributes
    with AuditedModel {
  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    val hasher = Hasher("md5")
    val id = for {
      organizationId <- parent.map(_.id)
      name           <- (attrs \ "name").asOpt[String]
      tpe            <- (attrs \ "type").asOpt[String]
    } yield hasher.fromString(s"${organizationId}_${name}_$tpe").head.toString
    Future.successful(attrs + ("_id" -> JsString(id.getOrElse("<null>"))))
  }
}

class Worker(model: WorkerModel, attributes: JsObject) extends EntityDef[WorkerModel, Worker](model, attributes) with WorkerAttributes {
  def config: JsObject = Try(Json.parse(configuration()).as[JsObject]).getOrElse(JsObject.empty)
  def organization     = parentId.get
}
