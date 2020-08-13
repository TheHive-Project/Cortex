package org.thp.cortex.services

import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration
import play.api.libs.json._

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.thp.cortex.models._
import org.scalactic.Accumulation._

import org.elastic4play.{AttributeCheckingError, NotFoundError}
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._

trait WorkerConfigSrv {
  val configuration: Configuration
  val userSrv: UserSrv
  val createSrv: CreateSrv
  val updateSrv: UpdateSrv
  val workerConfigModel: WorkerConfigModel
  val organizationSrv: OrganizationSrv
  val findSrv: FindSrv
  implicit val ec: ExecutionContext
  implicit val mat: Materializer

  val workerType: WorkerType.Type

  def definitions: Future[Map[String, BaseConfig]]

  protected def buildDefinitionMap(definitionSource: Source[WorkerDefinition, NotUsed]): Future[Map[String, BaseConfig]] =
    definitionSource
      .filter(_.baseConfiguration.isDefined)
      .map(d => d.copy(configurationItems = d.configurationItems.map(_.copy(required = false))))
      .groupBy(200, _.baseConfiguration.get) // TODO replace groupBy by fold to prevent "too many streams" error
      .map(d => BaseConfig(d.baseConfiguration.get, Seq(d.name), d.configurationItems, None))
      .reduce(_ + _)
      .filterNot(_.items.isEmpty)
      .mergeSubstreams
      .mapMaterializedValue(_ => NotUsed)
      .runWith(Sink.seq)
      .map { baseConfigs =>
        (BaseConfig.global(workerType, configuration) +: baseConfigs)
          .map(c => c.name -> c)
          .toMap
      }

  def getForUser(userId: String, configName: String): Future[BaseConfig] =
    userSrv
      .getOrganizationId(userId)
      .flatMap(organizationId => getForOrganization(organizationId, configName))

  def getForOrganization(organizationId: String, configName: String): Future[BaseConfig] = {
    import org.elastic4play.services.QueryDSL._
    for {
      workerConfig <- findForOrganization(organizationId, "name" ~= configName, Some("0-1"), Nil)
        ._1
        .runWith(Sink.headOption)
      d          <- definitions
      baseConfig <- d.get(configName).fold[Future[BaseConfig]](Future.failed(NotFoundError(s"config $configName not found")))(Future.successful)
    } yield baseConfig.copy(config = workerConfig)
  }

  def create(organization: Organization, fields: Fields)(implicit authContext: AuthContext): Future[WorkerConfig] =
    createSrv[WorkerConfigModel, WorkerConfig, Organization](workerConfigModel, organization, fields.set("type", workerType.toString))

  def update(workerConfig: WorkerConfig, fields: Fields)(implicit authContext: AuthContext): Future[WorkerConfig] =
    updateSrv(workerConfig, fields, ModifyConfig.default)

  def updateOrCreate(userId: String, workerConfigName: String, config: JsObject)(implicit authContext: AuthContext): Future[BaseConfig] =
    for {
      organizationId <- userSrv.getOrganizationId(userId)
      organization   <- organizationSrv.get(organizationId)
      baseConfig     <- getForOrganization(organizationId, workerConfigName)
      validatedConfig <- baseConfig
        .items
        .validatedBy(_.read(config))
        .map(_.filterNot(_._2 == JsNull))
        .fold(
          c => Future.successful(Fields.empty.set("config", JsObject(c).toString).set("name", workerConfigName)),
          errors => Future.failed(AttributeCheckingError("workerConfig", errors.toSeq))
        )
      newWorkerConfig <- baseConfig.config.fold(create(organization, validatedConfig))(workerConfig => update(workerConfig, validatedConfig))
    } yield baseConfig.copy(config = Some(newWorkerConfig))

  private def updateDefinitionConfig(definitionConfig: Map[String, BaseConfig], workerConfig: WorkerConfig): Map[String, BaseConfig] =
    definitionConfig
      .get(workerConfig.name())
      .fold(definitionConfig) { baseConfig =>
        definitionConfig + (workerConfig.name() -> baseConfig.copy(config = Some(workerConfig)))
      }

  def listConfigForUser(userId: String): Future[Seq[BaseConfig]] = {
    import org.elastic4play.services.QueryDSL._
    for {
      configItems <- definitions
      workerConfigs <- findForUser(userId, any, Some("all"), Nil)
        ._1
        .runFold(configItems) { (definitionConfig, workerConfig) =>
          updateDefinitionConfig(definitionConfig, workerConfig)
        }
    } yield workerConfigs.values.toSeq
  }

  def findForUser(userId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[WorkerConfig, NotUsed], Future[Long]) = {
    val configs = userSrv
      .getOrganizationId(userId)
      .map(organizationId => findForOrganization(organizationId, queryDef, range, sortBy))
    val configSource = Source.futureSource(configs.map(_._1)).mapMaterializedValue(_ => NotUsed)
    val configTotal  = configs.flatMap(_._2)
    configSource -> configTotal
  }

  def findForOrganization(
      organizationId: String,
      queryDef: QueryDef,
      range: Option[String],
      sortBy: Seq[String]
  ): (Source[WorkerConfig, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    find(and(withParent("organization", organizationId), "type" ~= workerType, queryDef), range, sortBy)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[WorkerConfig, NotUsed], Future[Long]) =
    findSrv[WorkerConfigModel, WorkerConfig](workerConfigModel, queryDef, range, sortBy)
}
