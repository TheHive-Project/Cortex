package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import play.api.libs.json.{ JsObject, JsString, Json }

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{ AttributeDef, BaseEntity, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F, AttributeOption ⇒ O }

object OrganizationStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Active, Locked = Value
  implicit val reads = enumFormat(this)
}

trait OrganizationAttributes { _: AttributeDef ⇒
  val name = attribute("name", F.stringFmt, "Organization name", O.form)
  val _id = attribute("_id", F.stringFmt, "Organization name", O.model)
  val description = attribute("description", F.stringFmt, "Organization description")
  val status = attribute("status", F.enumFmt(OrganizationStatus), "Status of the organization", OrganizationStatus.Active)
}

@Singleton
class OrganizationModel @Inject() () extends ModelDef[OrganizationModel, Organization]("organization", "Organization", "/organization") with OrganizationAttributes with AuditedModel {

  override def removeAttribute = Json.obj("status" -> "Locked")

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] =
    Future.successful {
      (attrs \ "name").asOpt[JsString].fold(attrs) { orgName ⇒
        attrs - "name" + ("_id" → orgName)
      }
    }
}

class Organization(model: OrganizationModel, attributes: JsObject) extends EntityDef[OrganizationModel, Organization](model, attributes) with OrganizationAttributes