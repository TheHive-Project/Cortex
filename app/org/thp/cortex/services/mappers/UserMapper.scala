package org.thp.cortex.services.mappers

import scala.concurrent.Future

import play.api.libs.json.JsValue

import org.elastic4play.controllers.Fields

/**
  * User mapper trait to be used when converting a JS response from a third party API to a valid Fields object. Used in
  * the SSO process to create new users if the option is selected.
  */
trait UserMapper {
  val name: String
  def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)] = None): Future[Fields]
}
