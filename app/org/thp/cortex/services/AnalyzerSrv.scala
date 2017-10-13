package org.thp.cortex.services

//
import java.nio.file.{ Files, Path }
import javax.inject.{ Inject, Singleton }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Try }

import play.api.Logger
import play.api.libs.json.{ JsObject, Json }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.thp.cortex.models._

import org.elastic4play.controllers.Fields
import org.elastic4play.services._

@Singleton
class AnalyzerSrv @Inject() (
    analyzerModel: AnalyzerModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {
  //    jobSrv: JobSrv,
  //    mispSrv: MispSrv,
  //    externalAnalyzerSrv: ExternalAnalyzerSrv) {
  //
  private lazy val logger = Logger(getClass)

  def get(analyzerId: String): Future[Analyzer] = getSrv[AnalyzerModel, Analyzer](analyzerModel, analyzerId)

  def create(fields: Fields)(implicit authContext: AuthContext): Future[Analyzer] = createSrv[AnalyzerModel, Analyzer](analyzerModel, fields)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[Analyzer] = {
    updateSrv[AnalyzerModel, Analyzer](analyzerModel, id, fields)
  }

  def update(analyzer: Analyzer, fields: Fields)(implicit authContext: AuthContext): Future[Analyzer] = {
    updateSrv(analyzer, fields)
  }

  //  def update(analyzer: Analyzer, analyzerDefinition: AnalyzerDefinition)(implicit authContext: AuthContext): Future[Analyzer] = {
  //    val fields = Fields(Json.obj(
  //      "description" -> analyzerDefinition.description,
  //      "dataTypeList" -> analyzerDefinition.dataTypeList,
  //      "author" -> analyzerDefinition.author,
  //      "url" -> analyzerDefinition.url,
  //      "license" -> analyzerDefinition.license,
  //      "configurationItems" -> analyzerDefinition.configuration
  //    ))
  //    update(analyzer, fields)
  //  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Analyzer, NotUsed], Future[Long]) = {
    findSrv[AnalyzerModel, Analyzer](analyzerModel, queryDef, range, sortBy)
  }

  def scan(analyzerPaths: Seq[Path])(implicit authContext: AuthContext) = {
    val analyzers = (for {
      analyzerPath ← analyzerPaths
      analyzerDir ← Try(Files.newDirectoryStream(analyzerPath).asScala).getOrElse {
        logger.warn(s"Analyzer directory ($analyzerPath) is not found")
        Nil
      }
      if Files.isDirectory(analyzerDir)
      infoFile ← Files.newDirectoryStream(analyzerDir, "*.json").asScala
      if Files.isReadable(infoFile)
      analyzer ← Try(Fields(readInfo(infoFile)))
        .recoverWith {
          case error ⇒
            logger.warn(s"Load of analyzer $infoFile fails", error)
            Failure(error)
        }
        .toOption
        .flatMap { analyerFields ⇒
          for {
            name ← analyerFields.getString("name")
            version ← analyerFields.getString("version")
            id = Analyzer.computeId(name, version)
            _ = logger.info(s"Register analyzer $name $version ($id)")
          } yield id -> analyerFields.set("path", analyzerDir.toString)
        }
    } yield analyzer)
      .toMap

    find(QueryDSL.any, Some("all"), Nil)._1
      .runFoldAsync(analyzers) {
        case (scannedAnalyzers, registeredAnalyzer) ⇒
          scannedAnalyzers.get(registeredAnalyzer.id) match {
            case Some(analyzerDefinition) ⇒
              update(registeredAnalyzer, analyzerDefinition)
                .map(_ ⇒ scannedAnalyzers - registeredAnalyzer.id)
            case None ⇒
              update(registeredAnalyzer, Fields.empty.set("status", AnalyzerStatus.Disabled.toString))
                .map(_ ⇒ scannedAnalyzers)
          }
      }
      .flatMap { newAnalyzers ⇒
        Future.traverse(newAnalyzers.values)(create)
      }
      .map(_ ⇒ ())
  }

  private def readInfo(file: Path): JsObject = {
    val source = scala.io.Source.fromFile(file.toFile)
    try Json.parse(source.mkString).as[JsObject]
    finally source.close()
  }

  //  def listForType(dataType: String): Source[Analyzer with Entity, Future[Long]] = {
  //    import QueryDSL._
  //    find()
  //    //Seq[Analyzer] = list.filter(_.dataTypeList.contains(dataType))
  //  }
  //
  //  def analyze(analyzerId: String, artifact: Artifact): Future[Job] = {
  //    get(analyzerId)
  //      .map { analyzer ⇒ analyze(analyzer, artifact) }
  //      .getOrElse(throw AnalyzerNotFoundError(analyzerId))
  //  }
  //
  //  def analyze(analyzer: Analyzer, artifact: Artifact): Future[Job] = {
  //    val report = analyzer match {
  //      case ea: ExternalAnalyzer ⇒ externalAnalyzerSrv.analyze(ea, artifact)
  //      case mm: MispModule       ⇒ mispSrv.analyze(mm, artifact)
  //    }
  //    jobSrv.create(analyzer, artifact, report)
  //  }
}