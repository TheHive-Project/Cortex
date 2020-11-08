package org.thp.cortex.models

import javax.inject.{Inject, Singleton}

import play.api.libs.json.{JsObject, Json}

import org.elastic4play.models.{AttributeDef, ChildModelDef, EntityDef, AttributeFormat => F, AttributeOption => O}

import org.thp.cortex.models.JsonFormat.workerTypeFormat

trait WorkerConfigAttributes { _: AttributeDef =>
  val name   = attribute("name", F.stringFmt, "Worker name")
  val config = attribute("config", F.rawFmt, "Configuration of worker", O.sensitive)
  val tpe    = attribute("type", F.enumFmt(WorkerType), "", O.readonly)
}

@Singleton
class WorkerConfigModel @Inject() (organizationModel: OrganizationModel)
    extends ChildModelDef[WorkerConfigModel, WorkerConfig, OrganizationModel, Organization](
      organizationModel,
      "workerConfig",
      "WorkerConfig",
      "/worker/config"
    )
    with WorkerConfigAttributes {}

class WorkerConfig(model: WorkerConfigModel, attributes: JsObject)
    extends EntityDef[WorkerConfigModel, WorkerConfig](model, attributes)
    with WorkerConfigAttributes {
  def organization = parentId.get
  def jsonConfig   = Json.parse(config()).as[JsObject]
}
