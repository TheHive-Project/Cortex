package org.thp.cortex.controllers

//
import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.libs.json.{ JsNull, Json }
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.thp.cortex.models.{ Analyzer, AnalyzerDefinition, Roles }
import org.thp.cortex.services.AnalyzerSrv

import org.elastic4play.controllers.{ Authenticated, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.QueryDSL

@Singleton
class AnalyzerCtrl @Inject() (
    analyzerSrv: AnalyzerSrv,
    //    jobSrv: JobSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends AbstractController(components) {

  def list = authenticated(Roles.read).async { request ⇒
    val (analyzers, analyzerTotal) = analyzerSrv.findForUser(request.userId, QueryDSL.any, Some("all"), Nil)
    renderer.toOutput(OK, analyzers, analyzerTotal)
  }

  def get(analyzerConfigId: String) = authenticated(Roles.read).async { request ⇒
    analyzerSrv.get(analyzerConfigId).map { analyzerConfig ⇒
      renderer.toOutput(OK, analyzerConfig)
    }
  }

  private val emptyAnalyzerDefinitionJson = Json.obj(
    "version" -> JsNull,
    "description" -> JsNull,
    "dataTypeList" -> Nil,
    "author" -> JsNull,
    "url" -> JsNull,
    "license" -> JsNull)

  private def analyzerJson(analyzer: Analyzer, analyzerDefinition: Option[AnalyzerDefinition]) = {
    analyzer.toJson ++ analyzerDefinition.fold(emptyAnalyzerDefinitionJson) { ad ⇒
      Json.obj(
        "analyzerDefinitionName" -> ad.name,
        "version" -> ad.version,
        "description" -> ad.description,
        "dataTypeList" -> ad.dataTypeList,
        "author" -> ad.author,
        "url" -> ad.url,
        "license" -> ad.license)
    }
  }

  def listForType(dataType: String): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    val (analyzers, _) = analyzerSrv.listForUser(request.userId)
    analyzers.mapAsyncUnordered(2)(a ⇒ analyzerSrv.getDefinition(a.analyzerDefinitionId()).map(a -> _))
      .collect {
        case (analyzer, analyzerDefinition) if analyzerDefinition.canProcessDataType(dataType) ⇒ analyzerJson(analyzer, Some(analyzerDefinition))
      }
      .runWith(Sink.seq)
      .map { analyzers ⇒
        renderer.toOutput(OK, analyzers)
      }
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
}