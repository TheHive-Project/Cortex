package org.thp.cortex.controllers
//
import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.mvc.{ AbstractController, ControllerComponents }

import org.thp.cortex.models.Roles
import org.thp.cortex.services.AnalyzerConfigSrv

import org.elastic4play.controllers.{ Authenticated, Renderer }
import org.elastic4play.services.QueryDSL
//
//import models.JsonFormat.{ analyzerWrites, dataActifactReads, jobWrites }
//import models.{ DataArtifact, FileArtifact }
//import play.api.libs.json.{ JsObject, JsString, Json }
//import play.api.mvc._
//
//import services.{ AnalyzerSrv, JobSrv }
//import scala.concurrent.{ ExecutionContext, Future }
//
@Singleton
class AnalyzerCtrl @Inject() (
                             analyzerConfigSrv: AnalyzerConfigSrv,
//    analyzerSrv: AnalyzerSrv,
//    jobSrv: JobSrv,
authenticated: Authenticated,
                             renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) {
//
  def list = authenticated(Roles.read) { request ⇒
    val analyzerConfigs = analyzerConfigSrv.findForUser(request.userId, QueryDSL.any, Some("all"), Nil)
    renderer.toOutput(OK, analyzerConfigs)
  }


//  def get(analyzerId: String) = Action { request ⇒
//    analyzerSrv.get(analyzerId) match {
//      case Some(analyzer) ⇒ Ok(Json.toJson(analyzer))
//      case None           ⇒ NotFound
//    }
//  }
//
//  private[controllers] def readDataArtifact(request: Request[AnyContent]) = {
//    for {
//      json ← request.body.asJson
//      artifact ← json.asOpt[DataArtifact]
//    } yield artifact
//  }
//
//  private[controllers] def readFileArtifact(request: Request[AnyContent]) = {
//    for {
//      parts ← request.body.asMultipartFormData
//      filePart ← parts.file("data")
//      attrList ← parts.dataParts.get("_json")
//      attrStr ← attrList.headOption
//      attr ← Json.parse(attrStr).asOpt[JsObject]
//    } yield FileArtifact(filePart.ref.file, attr +
//      ("content-type" → JsString(filePart.contentType.getOrElse("application/octet-stream"))) +
//      ("filename" → JsString(filePart.filename)))
//  }
//
//  def analyze(analyzerId: String): Action[AnyContent] = Action.async { request ⇒
//    readDataArtifact(request)
//      .orElse(readFileArtifact(request))
//      .map { artifact ⇒
//        analyzerSrv.analyze(analyzerId, artifact)
//          //jobSrv.create(artifact, analyzerId)
//          .map(j ⇒ Ok(Json.toJson(j)))
//      }
//      .getOrElse(Future.successful(BadRequest("???")))
//  }
//
  def listForType(dataType: String) = authenticated(Roles.read) { request ⇒

    Ok(Json.toJson(analyzerSrv.listForType(dataType)))
  }
}