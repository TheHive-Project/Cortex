package org.thp.cortex.services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json._

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import org.thp.cortex.models._
import org.scalactic.Accumulation._

import org.elastic4play.{ AttributeCheckingError, NotFoundError }
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._
import org.elastic4play.utils.Collection.distinctBy

case class BaseConfig(name: String, analyzerNames: Seq[String], items: Seq[ConfigurationDefinitionItem], config: Option[AnalyzerConfig]) {
  def +(other: BaseConfig) = BaseConfig(name, analyzerNames ++ other.analyzerNames, distinctBy(items ++ other.items)(_.name), config.orElse(other.config))
}
object BaseConfig {
  implicit val writes: Writes[BaseConfig] = Writes[BaseConfig] { baseConfig ⇒
    Json.obj(
      "name" -> baseConfig.name,
      "analyzers" -> baseConfig.analyzerNames,
      "configurationItems" -> baseConfig.items,
      "config" -> baseConfig.config.fold(JsObject.empty)(_.jsonConfig))
  }
  val global = BaseConfig("global", Nil, Seq(
    ConfigurationDefinitionItem("proxy_http", "url of http proxy", AnalyzerConfigItemType.string, multi = false, required = false, None),
    ConfigurationDefinitionItem("proxy_https", "url of https proxy", AnalyzerConfigItemType.string, multi = false, required = false, None),
    ConfigurationDefinitionItem("auto_extract_artifacts", "extract artifacts from full report automatically", AnalyzerConfigItemType.boolean, multi = false, required = false, Some(JsFalse))),
    None)
  val tlp = BaseConfig("tlp", Nil, Seq(
    ConfigurationDefinitionItem("check_tlp", "", AnalyzerConfigItemType.boolean, multi = false, required = false, None),
    ConfigurationDefinitionItem("max_tlp", "", AnalyzerConfigItemType.number, multi = false, required = false, None)),
    None)
}

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

  def definitions: Future[Map[String, BaseConfig]] =
    analyzerSrv.listDefinitions._1
      .filter(_.baseConfiguration.isDefined)
      .map(ad ⇒ ad.copy(configurationItems = ad.configurationItems.map(_.copy(required = false))))
      .groupBy(200, _.baseConfiguration.get)
      .map(ad ⇒ BaseConfig(ad.baseConfiguration.get, Seq(ad.name), ad.configurationItems, None))
      .reduce(_ + _)
      .filterNot(_.items.isEmpty)
      .mergeSubstreams
      .mapMaterializedValue(_ ⇒ NotUsed)
      .runWith(Sink.seq)
      .map { baseConfigs ⇒
        (BaseConfig.global +: baseConfigs)
          .map(c ⇒ c.name -> c)
          .toMap
      }

  def getForUser(userId: String, analyzerConfigName: String): Future[BaseConfig] = {
    userSrv.getOrganizationId(userId)
      .flatMap(organizationId ⇒ getForOrganization(organizationId, analyzerConfigName))
  }

  def getForOrganization(organizationId: String, analyzerConfigName: String): Future[BaseConfig] = {
    import org.elastic4play.services.QueryDSL._
    for {
      analyzerConfig ← findForOrganization(organizationId, "name" ~= analyzerConfigName, Some("0-1"), Nil)
        ._1
        .runWith(Sink.headOption)
      d ← definitions
      baseConfig ← d.get(analyzerConfigName).fold[Future[BaseConfig]](Future.failed(NotFoundError(s"analyzerConfig $analyzerConfigName not found")))(Future.successful)
    } yield baseConfig.copy(config = analyzerConfig)
  }

  def create(organization: Organization, fields: Fields)(implicit authContext: AuthContext): Future[AnalyzerConfig] = {
    createSrv[AnalyzerConfigModel, AnalyzerConfig, Organization](analyzerConfigModel, organization, fields)
  }

  def update(analyzerConfig: AnalyzerConfig, fields: Fields)(implicit authContext: AuthContext): Future[AnalyzerConfig] = {
    updateSrv(analyzerConfig, fields, ModifyConfig.default)
  }

  def updateOrCreate(userId: String, analyzerConfigName: String, config: JsObject)(implicit authContext: AuthContext): Future[BaseConfig] = {
    for {
      organizationId ← userSrv.getOrganizationId(userId)
      organization ← organizationSrv.get(organizationId)
      baseConfig ← getForOrganization(organizationId, analyzerConfigName)
      validatedConfig ← baseConfig.items.validatedBy(_.read(config))
        .map(_.filterNot(_._2 == JsNull))
        .fold(c ⇒ Future.successful(Fields.empty.set("config", JsObject(c).toString).set("name", analyzerConfigName)), errors ⇒ Future.failed(AttributeCheckingError("analyzerConfig", errors.toSeq)))
      newAnalyzerConfig ← baseConfig.config.fold(create(organization, validatedConfig))(analyzerConfig ⇒ update(analyzerConfig, validatedConfig))
    } yield baseConfig.copy(config = Some(newAnalyzerConfig))
  }

  def updateDefinitionConfig(definitionConfig: Map[String, BaseConfig], analyzerConfig: AnalyzerConfig): Map[String, BaseConfig] = {
    definitionConfig.get(analyzerConfig.name())
      .fold(definitionConfig) { baseConfig ⇒
        definitionConfig + (analyzerConfig.name() -> baseConfig.copy(config = Some(analyzerConfig)))
      }
  }

  def listForUser(userId: String): Future[Seq[BaseConfig]] = {
    import org.elastic4play.services.QueryDSL._
    for {
      analyzerConfigItems ← definitions
      analyzerConfigs ← findForUser(userId, any, Some("all"), Nil)
        ._1
        .runFold(analyzerConfigItems) { (definitionConfig, analyzerConfig) ⇒ updateDefinitionConfig(definitionConfig, analyzerConfig) }
    } yield analyzerConfigs.values.toSeq
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
