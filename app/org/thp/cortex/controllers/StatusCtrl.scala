package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{ JsString, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ AbstractController, ControllerComponents }

import com.sksamuel.elastic4s.ElasticDsl
import org.thp.cortex.models.Analyzer

import org.elastic4play.services.AuthSrv
import org.elastic4play.services.auth.MultiAuthSrv

@Singleton
class StatusCtrl @Inject() (
    configuration: Configuration,
    authSrv: AuthSrv,
    components: ControllerComponents) extends AbstractController(components) with Status {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def get = Action { _ ⇒
    Ok(Json.obj(
      "versions" → Json.obj(
        "Cortex" → getVersion(classOf[Analyzer]),
        "Elastic4Play" → getVersion(classOf[AuthSrv]),
        "Play" → getVersion(classOf[AbstractController]),
        "Elastic4s" → getVersion(classOf[ElasticDsl]),
        "ElasticSearch" → getVersion(classOf[org.elasticsearch.Build])),
      "config" → Json.obj(
        "authType" → (authSrv match {
          case multiAuthSrv: MultiAuthSrv ⇒ multiAuthSrv.authProviders.map { a ⇒ JsString(a.name) }
          case _                          ⇒ JsString(authSrv.name)
        }),
        "capabilities" → Json.arr())))
  }

  def health = TODO
}
