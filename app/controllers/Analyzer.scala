package controllers

import javax.inject.Inject

import scala.annotation.implicitNotFound
import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.{ JsObject, JsString, Json }
import play.api.mvc.{ Action, AnyContent, Controller, Request }

import models.{ DataArtifact, FileArtifact }
import models.JsonFormat.{ analyzerWrites, dataActifactReads, jobWrites }
import services.{ AnalyzerSrv, JobSrv }

class AnalyzerCtrl @Inject() (
    analyzerSrv: AnalyzerSrv,
    jobSrv: JobSrv,
    implicit val ec: ExecutionContext) extends Controller {

  def list = Action { request ⇒
    Ok(Json.toJson(analyzerSrv.list))
  }

  def get(analyzerId: String) = Action { request ⇒
    analyzerSrv.get(analyzerId) match {
      case Some(analyzer) ⇒ Ok(Json.toJson(analyzer))
      case None           ⇒ NotFound
    }
  }

  private[controllers] def readDataArtifact(request: Request[AnyContent]) = {
    for {
      json ← request.body.asJson
      artifact ← json.asOpt[DataArtifact]
    } yield artifact
  }

  private[controllers] def readFileArtifact(request: Request[AnyContent]) = {
    for {
      parts ← request.body.asMultipartFormData
      filePart ← parts.file("data").headOption
      attrList ← parts.dataParts.get("_json")
      attrStr ← attrList.headOption
      attr ← Json.parse(attrStr).asOpt[JsObject]
    } yield FileArtifact(filePart.ref.file, attr +
      ("content-type" → JsString(filePart.contentType.getOrElse("application/octet-stream"))) +
      ("filename" → JsString(filePart.filename)))
  }

  def analyze(analyzerId: String) = Action.async { request ⇒
    readDataArtifact(request)
      .orElse(readFileArtifact(request))
      .map { artifact ⇒
        jobSrv.create(artifact, analyzerId)
          .map(j ⇒ Ok(Json.toJson(j)))
      }
      .getOrElse(Future.successful(BadRequest("???")))
  }

  def listForType(dataType: String) = Action { request ⇒
    Ok(Json.toJson(analyzerSrv.listForType(dataType)))
  }
}