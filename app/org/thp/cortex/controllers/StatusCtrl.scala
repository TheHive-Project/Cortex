package org.thp.cortex.controllers

import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsBoolean, JsNull, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import com.sksamuel.elastic4s.ElasticDsl
import org.elastic4play.controllers.Authenticated

import javax.inject.{Inject, Singleton}
import org.elasticsearch.client.Node
import org.thp.cortex.models.{Roles, Worker, WorkerType}
import org.elastic4play.services.AuthSrv
import org.elastic4play.services.auth.MultiAuthSrv
import org.thp.cortex.services.WorkerSrv

@Singleton
class StatusCtrl @Inject() (
    configuration: Configuration,
    authSrv: AuthSrv,
    workerSrv: WorkerSrv,
    components: ControllerComponents,
    authenticated: Authenticated,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def get: Action[AnyContent] =
    Action {
      Ok(
        Json.obj(
          "versions" -> Json.obj(
            "Cortex"               -> getVersion(classOf[Worker]),
            "Elastic4Play"         -> getVersion(classOf[AuthSrv]),
            "Play"                 -> getVersion(classOf[AbstractController]),
            "Elastic4s"            -> getVersion(classOf[ElasticDsl]),
            "ElasticSearch client" -> getVersion(classOf[Node])
          ),
          "config" -> Json.obj(
            "protectDownloadsWith" -> configuration.get[String]("datastore.attachment.password"),
            "authType" -> (authSrv match {
              case multiAuthSrv: MultiAuthSrv =>
                multiAuthSrv.authProviders.map { a =>
                  JsString(a.name)
                }
              case _ => JsString(authSrv.name)
            }),
            "capabilities" -> authSrv.capabilities.map(c => JsString(c.toString)),
            "ssoAutoLogin" -> JsBoolean(configuration.getOptional[Boolean]("auth.sso.autologin").getOrElse(false))
          )
        )
      )
    }

  def getAlerts: Action[AnyContent] =
    authenticated(Roles.read).async { implicit request =>
      workerSrv.obsoleteWorkersForUser(request.userId).map { obsoleteWorkers =>
        val (obsoleteAnalyzers, obsoleteResponders) = obsoleteWorkers.partition(_.tpe() == WorkerType.analyzer)
        val alerts =
          (if (obsoleteAnalyzers.nonEmpty) List("ObsoleteAnalyzers") else Nil) :::
            (if (obsoleteResponders.nonEmpty) List("ObsoleteResponders") else Nil)
        Ok(Json.toJson(alerts))
      }
    }

  def health: Action[AnyContent] = TODO
}
