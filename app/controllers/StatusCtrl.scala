package controllers

import javax.inject.{ Inject, Singleton }

import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ Action, Controller }

@Singleton
class StatusCtrl @Inject() (
    configuration: Configuration) extends Controller {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def get = Action { _ ⇒
    Ok(Json.obj(
      "versions" → Json.obj(
        "Cortex" → getVersion(classOf[models.Artifact]),
        "Play" → getVersion(classOf[Controller])),
      "config" → Json.obj(
        "authType" → "none",
        "capabilities" → Json.arr())))
  }
}
