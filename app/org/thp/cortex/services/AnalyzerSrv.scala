package org.thp.cortex.services

import java.nio.file.{ Files, Path, Paths }
import javax.inject.{ Inject, Singleton }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.libs.json.JsObject
import play.api.{ Configuration, Logger }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import org.thp.cortex.models._

import org.elastic4play.{ AttributeCheckingError, MultiError, NotFoundError }
import org.elastic4play.controllers.{ Fields, StringInputValue }
import org.elastic4play.services._
import org.scalactic._
import org.scalactic.Accumulation._

import org.elastic4play.database.ModifyConfig

@Singleton
class AnalyzerSrv(
    analyzersPaths: Seq[Path],
    analyzerModel: AnalyzerModel,
    organizationSrv: OrganizationSrv,
    userSrv: UserSrv,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  @Inject() def this(
      config: Configuration,
      analyzerModel: AnalyzerModel,
      organizationSrv: OrganizationSrv,
      userSrv: UserSrv,
      createSrv: CreateSrv,
      getSrv: GetSrv,
      updateSrv: UpdateSrv,
      deleteSrv: DeleteSrv,
      findSrv: FindSrv,
      ec: ExecutionContext,
      mat: Materializer) = this(
    config.get[Seq[String]]("analyzer.path").map(p ⇒ Paths.get(p)),
    analyzerModel,
    organizationSrv,
    userSrv,
    createSrv,
    getSrv,
    updateSrv,
    deleteSrv,
    findSrv,
    ec,
    mat)

  private lazy val logger = Logger(getClass)
  private var analyzerMap = Map.empty[String, AnalyzerDefinition]

  private object analyzerMapLock

  rescan()

  def getDefinition(analyzerId: String): Future[AnalyzerDefinition] = analyzerMap.get(analyzerId) match {
    case Some(analyzer) ⇒ Future.successful(analyzer)
    case None           ⇒ Future.failed(NotFoundError(s"Analyzer $analyzerId not found"))
  }

  def listDefinitions: (Source[AnalyzerDefinition, NotUsed], Future[Long]) = Source(analyzerMap.values.toList) -> Future.successful(analyzerMap.size.toLong)

  def get(analyzerId: String): Future[Analyzer] = getSrv[AnalyzerModel, Analyzer](analyzerModel, analyzerId)

  def getForUser(userId: String, analyzerId: String): Future[Analyzer] = {
    userSrv.get(userId)
      .flatMap(user ⇒ getForOrganization(user.organization(), analyzerId))
  }

  def getForOrganization(organizationId: String, analyzerId: String): Future[Analyzer] = {
    import org.elastic4play.services.QueryDSL._
    find(
      and(withParent("organization", organizationId), "analyzerId" ~= analyzerId),
      Some("0-1"), Nil)._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Configuration for analyzer $analyzerId not found for organization $organizationId")))
  }

  def listForOrganization(organizationId: String): (Source[Analyzer, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    findForOrganization(organizationId, any, Some("all"), Nil)
  }

  def listForUser(userId: String): (Source[Analyzer, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    findForUser(userId, any, Some("all"), Nil)
  }

  def findForUser(userId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Analyzer, NotUsed], Future[Long]) = {
    val analyzers = for {
      user ← userSrv.get(userId)
      organizationId = user.organization()
    } yield findForOrganization(organizationId, queryDef, range, sortBy)
    val analyserSource = Source.fromFutureSource(analyzers.map(_._1)).mapMaterializedValue(_ ⇒ NotUsed)
    val analyserTotal = analyzers.flatMap(_._2)
    analyserSource -> analyserTotal
  }

  def findForOrganization(organizationId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Analyzer, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    find(and(withParent("organization", organizationId), queryDef), range, sortBy)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Analyzer, NotUsed], Future[Long]) = {
    findSrv[AnalyzerModel, Analyzer](analyzerModel, queryDef, range, sortBy)
  }

  def rescan(): Unit = {
    scan(analyzersPaths)
  }

  def scan(analyzerPaths: Seq[Path]): Unit = {
    val analyzers = (for {
      analyzerPath ← analyzerPaths
      analyzerDir ← Try(Files.newDirectoryStream(analyzerPath).asScala).getOrElse {
        logger.warn(s"Analyzer directory ($analyzerPath) is not found")
        Nil
      }
      if Files.isDirectory(analyzerDir)
      infoFile ← Files.newDirectoryStream(analyzerDir, "*.json").asScala
      analyzerDefinition ← AnalyzerDefinition.fromPath(infoFile).fold(
        error ⇒ {
          logger.warn("Analyzer definition file read error", error)
          Nil
        },
        ad ⇒ Seq(ad))
    } yield analyzerDefinition.id -> analyzerDefinition)
      .toMap

    analyzerMapLock.synchronized {
      analyzerMap = analyzers
    }
    logger.info(s"New analyzer list:\n\n\t${analyzerMap.values.map(a ⇒ s"${a.name} ${a.version}").mkString("\n\t")}\n")
  }

  def create(organization: Organization, analyzerDefinition: AnalyzerDefinition, analyzerFields: Fields)(implicit authContext: AuthContext): Future[Analyzer] = {
    val rawConfig = analyzerFields.getValue("configuration").fold(JsObject.empty)(_.as[JsObject])
    val configOrErrors = analyzerDefinition.configurationItems
      .validatedBy { cdi ⇒
        cdi.read(rawConfig)
      }
      .map(JsObject.apply)
      .badMap(attributeErrors ⇒ AttributeCheckingError(s"analyzer(${analyzerDefinition.name}).configuration", attributeErrors))
      .accumulating

    val unknownConfigItems = (rawConfig.value.keySet -- analyzerDefinition.configurationItems.map(_.name))
      .foldLeft[Unit Or Every[CortexError]](Good(())) {
        case (Good(_), ci) ⇒ Bad(One(UnknownConfigurationItem(ci)))
        case (Bad(e), ci)  ⇒ Bad(UnknownConfigurationItem(ci) +: e)
      }

    withGood(configOrErrors, unknownConfigItems)((c, _) ⇒ c)
      .fold(cfg ⇒ {
        createSrv[AnalyzerModel, Analyzer, Organization](analyzerModel, organization, analyzerFields
          .set("analyzerDefinitionId", analyzerDefinition.id)
          .set("description", analyzerDefinition.description)
          .set("configuration", cfg.toString)
          .addIfAbsent("dataTypeList", StringInputValue(analyzerDefinition.dataTypeList)))

      }, {
        case One(e)         ⇒ Future.failed(e)
        case Every(es @ _*) ⇒ Future.failed(MultiError("Analyzer creation failure", es))
      })
  }

  def create(organizationId: String, analyzerDefinitionId: String, analyzerFields: Fields)(implicit authContext: AuthContext): Future[Analyzer] = {
    for {
      organization ← organizationSrv.get(organizationId)
      analyzerDefinition ← getDefinition(analyzerDefinitionId)
      analyzer ← create(organization, analyzerDefinition, analyzerFields)
    } yield analyzer
  }

  def delete(analyzerId: String)(implicit authContext: AuthContext): Future[Unit] =
    deleteSrv.realDelete[AnalyzerModel, Analyzer](analyzerModel, analyzerId)

  def update(analyzerId: String, fields: Fields, modifyConfig: ModifyConfig = ModifyConfig.default)(implicit authContext: AuthContext): Future[Analyzer] = {
    val analyzerFields = fields.getValue("configuration").fold(fields)(cfg ⇒ fields.set("configuration", cfg.toString))
    updateSrv[AnalyzerModel, Analyzer](analyzerModel, analyzerId, analyzerFields, modifyConfig)
  }
}