package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.{ AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F }
import org.thp.cortex.models.JsonFormat.subscriptionStatusFormat

object SubscriptionStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Active, Locked = Value
}

trait SubscriptionAttributes { _: AttributeDef ⇒
  val name = attribute("name", F.stringFmt, "Subscription name")
  val description = attribute("title", F.stringFmt, "Subscription description")
  val status = attribute("status", F.enumFmt(SubscriptionStatus), "Status of the subscription", SubscriptionStatus.Active)
}

@Singleton
class SubscriptionModel @Inject() () extends ModelDef[SubscriptionModel, Subscription]("subscription", "Subscription", "/subscription") with SubscriptionAttributes {
}

class Subscription(model: SubscriptionModel, attributes: JsObject) extends EntityDef[SubscriptionModel, Subscription](model, attributes) with SubscriptionAttributes