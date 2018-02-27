package org.thp.cortex.services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.{ JsNull, JsObject }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import org.thp.cortex.models._
import org.scalactic._
import org.scalactic.Accumulation._

import org.elastic4play.{ AttributeCheckingError, NotFoundError }
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._
import org.elastic4play.utils.Collection.distinctBy

@Singleton
class AnalyzerConfigSrv @Inject() (
    analyzerConfigModel: AnalyzerConfigModel,
    userSrv: UserSrv,
    organizationSrv: OrganizationSrv,
    analyzerSrv: AnalyzerSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  private val proxyConfigurationItem = Seq(
    ConfigurationDefinitionItem("proxy_http", "url of http proxy", AnalyzerConfigItemType.string, multi = false, required = false, None),
    ConfigurationDefinitionItem("proxy_https", "url of https proxy", AnalyzerConfigItemType.string, multi = false, required = false, None))

  def definitions: Future[Map[String, Seq[ConfigurationDefinitionItem]]] =
    analyzerSrv.listDefinitions._1
      .filter(_.baseConfiguration.isDefined)
      .map(ad ⇒ ad.copy(configurationItems = ad.configurationItems.map(_.copy(required = false))))
      .groupBy(100, _.baseConfiguration.get)
      .map(ad ⇒ ad.baseConfiguration.get -> ad.configurationItems)
      .reduce((ad1, ad2) ⇒ ad1._1 -> distinctBy(ad1._2 ++ ad2._2)(_.name))
      .mergeSubstreams
      .mapMaterializedValue(_ ⇒ NotUsed)
      .runWith(Sink.seq)
      .map(_.toMap + ("global" -> proxyConfigurationItem))

  def definitionsWithEmptyConfig: Future[Map[String, (Seq[ConfigurationDefinitionItem], Option[AnalyzerConfig])]] = definitions.map(_.mapValues(_ -> None))

  def getDefinition(analyzerConfigName: String): Future[Seq[ConfigurationDefinitionItem]] = {
    definitions.flatMap { configList ⇒
      configList.get(analyzerConfigName)
        .fold[Future[Seq[ConfigurationDefinitionItem]]](Future.failed(NotFoundError(s"analyzerConfig $analyzerConfigName not found")))(Future.successful)
    }
  }

  def getForUser(userId: String, analyzerConfigName: String): Future[(Seq[ConfigurationDefinitionItem], Option[AnalyzerConfig])] = {
    userSrv.getOrganizationId(userId)
      .flatMap(organizationId ⇒ getForOrganization(organizationId, analyzerConfigName))
  }

  def getForOrganization(organizationId: String, analyzerConfigName: String): Future[(Seq[ConfigurationDefinitionItem], Option[AnalyzerConfig])] = {
    import org.elastic4play.services.QueryDSL._
    for {
      analyzerConfig ← findForOrganization(organizationId, "name" ~= analyzerConfigName, Some("0-1"), Nil)
        ._1
        .runWith(Sink.headOption)
      configDefinition ← getDefinition(analyzerConfigName)
    } yield configDefinition -> analyzerConfig
  }

  def create(organization: Organization, fields: Fields)(implicit authContext: AuthContext): Future[AnalyzerConfig] = {
    createSrv[AnalyzerConfigModel, AnalyzerConfig, Organization](analyzerConfigModel, organization, fields)
  }

  def update(analyzerConfig: AnalyzerConfig, fields: Fields)(implicit authContext: AuthContext): Future[AnalyzerConfig] = {
    updateSrv(analyzerConfig, fields, ModifyConfig.default)
  }

  def updateOrCreate(userId: String, analyzerConfigName: String, config: JsObject)(implicit authContext: AuthContext): Future[(Seq[ConfigurationDefinitionItem], AnalyzerConfig)] = {
    for {
      organizationId ← userSrv.getOrganizationId(userId)
      organization ← organizationSrv.get(organizationId)
      (configurationItems, maybeAnalyzerConfig) ← getForOrganization(organizationId, analyzerConfigName)
      validatedConfig ← configurationItems.validatedBy(_.read(config))
        .map(_.filterNot(_._2 == JsNull))
        .fold(c ⇒ Future.successful(Fields.empty.set("config", JsObject(c).toString).set("name", analyzerConfigName)), errors ⇒ Future.failed(AttributeCheckingError("analyzerConfig", errors.toSeq)))
      newAnalyzerConfig ← maybeAnalyzerConfig.fold(create(organization, validatedConfig))(analyzerConfig ⇒ update(analyzerConfig, validatedConfig))
    } yield configurationItems -> newAnalyzerConfig
  }

  def updateDefinitionConfig(definitionConfig: Map[String, (Seq[ConfigurationDefinitionItem], Option[AnalyzerConfig])], analyzerConfig: AnalyzerConfig): Map[String, (Seq[ConfigurationDefinitionItem], Option[AnalyzerConfig])] = {
    definitionConfig.get(analyzerConfig.name())
      .fold(definitionConfig) {
        case (definition, _) ⇒ definitionConfig + (analyzerConfig.name() -> (definition -> Some(analyzerConfig)))
      }
  }

  def listForUser(userId: String): Future[Map[String, (Seq[ConfigurationDefinitionItem], Option[AnalyzerConfig])]] = {
    import org.elastic4play.services.QueryDSL._
    for {
      analyzerConfigItems ← definitionsWithEmptyConfig
      analyzerConfigs ← findForUser(userId, any, Some("all"), Nil)
        ._1
        .runFold(analyzerConfigItems) { (definitionConfig, analyzerConfig) ⇒ updateDefinitionConfig(definitionConfig, analyzerConfig) }
    } yield analyzerConfigs
  }

  def findForUser(userId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[AnalyzerConfig, NotUsed], Future[Long]) = {
    val analyzerConfigs = userSrv.getOrganizationId(userId)
      .map(organizationId ⇒ findForOrganization(organizationId, queryDef, range, sortBy))
    val analyserConfigSource = Source.fromFutureSource(analyzerConfigs.map(_._1)).mapMaterializedValue(_ ⇒ NotUsed)
    val analyserConfigTotal = analyzerConfigs.flatMap(_._2)
    analyserConfigSource -> analyserConfigTotal
  }

  def findForOrganization(organizationId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[AnalyzerConfig, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    find(and(withParent("organization", organizationId), queryDef), range, sortBy)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[AnalyzerConfig, NotUsed], Future[Long]) = {
    findSrv[AnalyzerConfigModel, AnalyzerConfig](analyzerConfigModel, queryDef, range, sortBy)
  }

}
