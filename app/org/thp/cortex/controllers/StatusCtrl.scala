package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ AbstractController, ControllerComponents }

import org.thp.cortex.models.Analyzer

@Singleton
class StatusCtrl @Inject() (
    configuration: Configuration,
    components: ControllerComponents) extends AbstractController(components) with Status {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def get = Action { _ ⇒
    Ok(Json.obj(
      "versions" → Json.obj(
        "Cortex" → getVersion(classOf[Analyzer]),
        "Play" → getVersion(classOf[AbstractController])),
      "config" → Json.obj(
        "authType" → "none",
        "capabilities" → Json.arr())))
  }

  def health = TODO
}
