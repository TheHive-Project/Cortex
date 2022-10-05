package org.thp.cortex.services

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.elastic4play._
import org.elastic4play.controllers.{Fields, StringInputValue}
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services.QueryDSL.any
import org.elastic4play.services._
import org.scalactic.Accumulation._
import org.scalactic._
import org.thp.cortex.models._
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.{Configuration, Logger}

import java.net.URL
import java.nio.file.{Files, Path, Paths}
import javax.inject.{Inject, Provider, Singleton}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Codec
import scala.util.{Failure, Success, Try}

@Singleton
class WorkerSrv @Inject() (
    config: Configuration,
    workerModel: WorkerModel,
    organizationSrv: OrganizationSrv,
    jobRunnerSrvProvider: Provider[JobRunnerSrv],
    userSrv: UserSrv,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    ws: CustomWSAPI,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  private lazy val logger                     = Logger(getClass)
  private val analyzersURLs: Seq[String]      = config.getDeprecated[Seq[String]]("analyzer.urls", "analyzer.path")
  private val respondersURLs: Seq[String]     = config.getDeprecated[Seq[String]]("responder.urls", "responder.path")
  private lazy val jobRunnerSrv: JobRunnerSrv = jobRunnerSrvProvider.get
  private var workerMap                       = Map.empty[String, WorkerDefinition]
  private object workerMapLock

  rescan()

  def getDefinition(workerId: String): Try[WorkerDefinition] =
    workerMap.get(workerId) match {
      case Some(worker) => Success(worker)
      case None         => Failure(NotFoundError(s"Worker $workerId not found"))
    }

  //  def listDefinitions: (Source[WorkerDefinition, NotUsed], Future[Long]) = Source(workerMap.values.toList) → Future.successful(workerMap.size.toLong)

  def listAnalyzerDefinitions: (Source[WorkerDefinition, NotUsed], Future[Long]) = {
    val analyzerDefinitions = workerMap.values.filter(_.tpe == WorkerType.analyzer)
    Source(analyzerDefinitions.toList) -> Future.successful(analyzerDefinitions.size.toLong)
  }

  def listResponderDefinitions: (Source[WorkerDefinition, NotUsed], Future[Long]) = {
    val responderDefinitions = workerMap.values.filter(_.tpe == WorkerType.responder)
    Source(responderDefinitions.toList) -> Future.successful(responderDefinitions.size.toLong)
  }

  def get(workerId: String): Future[Worker] = getSrv[WorkerModel, Worker](workerModel, workerId)

  def getForUser(userId: String, workerId: String): Future[Worker] =
    userSrv
      .getOrganizationId(userId)
      .flatMap(organization => getForOrganization(organization, workerId))

  def getForOrganization(organizationId: String, workerId: String): Future[Worker] = {
    import org.elastic4play.services.QueryDSL._
    find(and(withParent("organization", organizationId), withId(workerId)), Some("0-1"), Nil)
      ._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"worker $workerId not found")))
  }

  def findAnalyzersForUser(
      userId: String,
      queryDef: QueryDef,
      range: Option[String],
      sortBy: Seq[String]
  ): (Source[Worker, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    val analyzers = for {
      user <- userSrv.get(userId)
      organizationId = user.organization()
    } yield findForOrganization(organizationId, and(queryDef, "type" ~= WorkerType.analyzer), range, sortBy)
    val analyserSource = Source.futureSource(analyzers.map(_._1)).mapMaterializedValue(_ => NotUsed)
    val analyserTotal  = analyzers.flatMap(_._2)
    analyserSource -> analyserTotal
  }

  def findRespondersForUser(
      userId: String,
      queryDef: QueryDef,
      range: Option[String],
      sortBy: Seq[String]
  ): (Source[Worker, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    val responders = for {
      user <- userSrv.get(userId)
      organizationId = user.organization()
    } yield findForOrganization(organizationId, and(queryDef, "type" ~= WorkerType.responder), range, sortBy)
    val analyserSource = Source.futureSource(responders.map(_._1)).mapMaterializedValue(_ => NotUsed)
    val analyserTotal  = responders.flatMap(_._2)
    analyserSource -> analyserTotal
  }

  private def findForOrganization(
      organizationId: String,
      queryDef: QueryDef,
      range: Option[String],
      sortBy: Seq[String]
  ): (Source[Worker, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    find(and(withParent("organization", organizationId), queryDef), range, sortBy)
  }

  private def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Worker, NotUsed], Future[Long]) =
    findSrv[WorkerModel, Worker](workerModel, queryDef, range, sortBy)

  def rescan(): Unit =
    scan(
      analyzersURLs.map(_    -> WorkerType.analyzer) ++
        respondersURLs.map(_ -> WorkerType.responder)
    )

  def obsoleteWorkersForUser(userId: String): Future[Seq[Worker]] =
    userSrv.get(userId).flatMap { user =>
      obsoleteWorkersForOrganization(user.organization())
    }

  def obsoleteWorkersForOrganization(organizationId: String): Future[Seq[Worker]] = {
    import org.elastic4play.services.QueryDSL._
    find(withParent("organization", organizationId), Some("all"), Nil)
      ._1
      .filterNot(worker => workerMap.contains(worker.workerDefinitionId()))
      .runWith(Sink.seq)
  }

  def scan(workerUrls: Seq[(String, WorkerType.Type)]): Future[Unit] = {
    def readUrl(url: URL, workerType: WorkerType.Type): Future[Seq[WorkerDefinition]] =
      url.getProtocol match {
        case "file" => Future.successful(readFile(Paths.get(url.toURI), workerType))
        case "http" | "https" =>
          val reads = WorkerDefinition.reads(workerType)
          val query = ws.url(url.toString).get()
          logger.debug(s"Read catalog using query $query")
          query
            .map(response => response.json.as(reads))
            .map(_.filterNot(_.command.isDefined))
      }

    def readFile(path: Path, workerType: WorkerType.Type): Seq[WorkerDefinition] = {
      val reads         = WorkerDefinition.reads(workerType)
      val source        = scala.io.Source.fromFile(path.toFile)(Codec.UTF8)
      lazy val basePath = path.getParent.getParent
      val workerDefinitions =
        for {
          w <- Try(source.mkString).map(Json.parse(_).as(reads)).getOrElse {
            logger.error(s"File $path has invalid format")
            Nil
          }
          command = w.command.map(cmd => basePath.resolve(cmd))
          if command.isEmpty || command.exists(_.normalize().startsWith(basePath))
        } yield w.copy(command = command)
      source.close()
      workerDefinitions.filter {
        case w if w.command.isDefined && jobRunnerSrv.processRunnerIsEnable    => true
        case w if w.dockerImage.isDefined && jobRunnerSrv.dockerRunnerIsEnable => true
        case w =>
          val reason =
            if (w.command.isDefined) "process runner is disabled"
            else if (w.dockerImage.isDefined) "Docker runner is disabled"
            else "it doesn't have image nor command"

          logger.warn(s"$workerType ${w.name} is disabled because $reason")
          false
      }
    }

    def readDirectory(path: Path, workerType: WorkerType.Type): Seq[WorkerDefinition] =
      for {
        workerDir <- Files.newDirectoryStream(path).asScala.toSeq
        if Files.isDirectory(workerDir)
        infoFile         <- Files.newDirectoryStream(workerDir, "*.json").asScala
        workerDefinition <- readFile(infoFile, workerType)
      } yield workerDefinition

    Future
      .traverse(workerUrls) {
        case (workerUrl, workerType) =>
          Future(new URL(workerUrl))
            .flatMap(readUrl(_, workerType))
            .recover {
              case error =>
                val path = Paths.get(workerUrl)
                if (Files.isRegularFile(path)) readFile(path, workerType)
                else if (Files.isDirectory(path)) readDirectory(path, workerType)
                else {
                  logger.warn(s"Worker path ($workerUrl) is not found", error)
                  Nil
                }
            }
      }
      .map { worker =>
        val wmap = worker.flatten.map(w => w.id -> w).toMap
        workerMapLock.synchronized(workerMap = wmap)
        logger.info(s"New worker list:\n\n\t${workerMap.values.map(a => s"${a.name} ${a.version}").mkString("\n\t")}\n")
      }

  }

  def create(organization: Organization, workerDefinition: WorkerDefinition, workerFields: Fields)(implicit
      authContext: AuthContext
  ): Future[Worker] = {
    val rawConfig = workerFields.getValue("configuration").fold(JsObject.empty)(_.as[JsObject])
    val configItems = workerDefinition.configurationItems ++ BaseConfig.global(workerDefinition.tpe, config).items ++ BaseConfig
      .tlp
      .items ++ BaseConfig.pap.items
    val configOrErrors = configItems
      .validatedBy(_.read(rawConfig))
      .map(JsObject.apply)

    val unknownConfigItems = (rawConfig.value.keySet -- configItems.map(_.name))
      .foldLeft[Unit Or Every[AttributeError]](Good(())) {
        case (Good(_), ci) => Bad(One(UnknownAttributeError("worker.config", JsString(ci))))
        case (Bad(e), ci)  => Bad(UnknownAttributeError("worker.config", JsString(ci)) +: e)
      }

    withGood(configOrErrors, unknownConfigItems)((c, _) => c)
      .fold(
        cfg =>
          createSrv[WorkerModel, Worker, Organization](
            workerModel,
            organization,
            workerFields
              .set("workerDefinitionId", workerDefinition.id)
              .set("description", workerDefinition.description)
              .set("author", workerDefinition.author)
              .set("version", workerDefinition.version)
              .set("dockerImage", workerDefinition.dockerImage.map(JsString))
              .set("command", workerDefinition.command.map(p => JsString(p.toString)))
              .set("url", workerDefinition.url)
              .set("license", workerDefinition.license)
              .set("baseConfig", workerDefinition.baseConfiguration.fold(JsString(workerDefinition.name))(JsString.apply))
              .set("configuration", cfg.toString)
              .set("type", workerDefinition.tpe.toString)
              .addIfAbsent("dataTypeList", StringInputValue(workerDefinition.dataTypeList))
          ),
        {
          case One(e)         => Future.failed(e)
          case Every(es @ _*) => Future.failed(AttributeCheckingError(s"worker(${workerDefinition.name}).configuration", es))
        }
      )
  }

  def create(organizationId: String, workerDefinition: WorkerDefinition, workerFields: Fields)(implicit authContext: AuthContext): Future[Worker] =
    for {
      organization <- organizationSrv.get(organizationId)
      worker       <- create(organization, workerDefinition, workerFields)
    } yield worker

  def delete(worker: Worker)(implicit authContext: AuthContext): Future[Unit] =
    deleteSrv.realDelete(worker)

  def delete(workerId: String)(implicit authContext: AuthContext): Future[Unit] =
    deleteSrv.realDelete[WorkerModel, Worker](workerModel, workerId)

  def update(worker: Worker, fields: Fields)(implicit authContext: AuthContext): Future[Worker] = update(worker, fields, ModifyConfig.default)

  def update(worker: Worker, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Worker] = {
    val workerFields = fields.getValue("configuration").fold(fields)(cfg => fields.set("configuration", cfg.toString))
    updateSrv(worker, workerFields, modifyConfig)
  }

  def update(workerId: String, fields: Fields)(implicit authContext: AuthContext): Future[Worker] = update(workerId, fields, ModifyConfig.default)

  def update(workerId: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Worker] =
    get(workerId).flatMap(worker => update(worker, fields, modifyConfig))
}
