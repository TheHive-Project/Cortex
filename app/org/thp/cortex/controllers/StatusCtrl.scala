package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{ JsString, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import com.sksamuel.elastic4s.ElasticDsl
import org.thp.cortex.models.Worker

import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import org.elastic4play.services.auth.MultiAuthSrv

@Singleton
class StatusCtrl @Inject() (
    configuration: Configuration,
    authSrv: AuthSrv,
    dbIndex: DBIndex,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def get: Action[AnyContent] = Action.async { _ ⇒
    dbIndex.clusterVersions.map { versions ⇒
      Ok(Json.obj(
        "versions" → Json.obj(
          "Cortex" → getVersion(classOf[Worker]),
          "Elastic4Play" → getVersion(classOf[AuthSrv]),
          "Play" → getVersion(classOf[AbstractController]),
          "Elastic4s" → getVersion(classOf[ElasticDsl]),
          "ElasticSearch client" → getVersion(classOf[org.elasticsearch.Build]),
          "ElasticSearch cluster" → versions.mkString(", ")),
        "config" → Json.obj(
          "authType" → (authSrv match {
            case multiAuthSrv: MultiAuthSrv ⇒ multiAuthSrv.authProviders.map { a ⇒ JsString(a.name) }
            case _                          ⇒ JsString(authSrv.name)
          }),
          "capabilities" → authSrv.capabilities.map(c ⇒ JsString(c.toString)))))
    }
  }

  def health: Action[AnyContent] = TODO
}
