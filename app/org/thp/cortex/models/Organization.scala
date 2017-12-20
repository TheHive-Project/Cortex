package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{ AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F }

object OrganizationStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Active, Locked = Value
  implicit val reads = enumFormat(this)
}

trait OrganizationAttributes { _: AttributeDef ⇒
  val name = attribute("name", F.stringFmt, "Organization name")
  val description = attribute("title", F.stringFmt, "Organization description")
  val status = attribute("status", F.enumFmt(OrganizationStatus), "Status of the organization", OrganizationStatus.Active)
}

@Singleton
class OrganizationModel @Inject() () extends ModelDef[OrganizationModel, Organization]("organization", "Organization", "/organization") with OrganizationAttributes with AuditedModel {
}

class Organization(model: OrganizationModel, attributes: JsObject) extends EntityDef[OrganizationModel, Organization](model, attributes) with OrganizationAttributes