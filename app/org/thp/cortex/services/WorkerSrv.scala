package org.thp.cortex.services

import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.elastic4play._
import org.elastic4play.controllers.{Fields, StringInputValue}
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services.{UserSrv => _, _}
import org.scalactic.Accumulation._
import org.scalactic._
import org.thp.cortex.models._
import play.api.libs.json.{JsNull, JsObject, JsString, Json}
import play.api.{Configuration, Logger}

import java.net.URL
import java.nio.file.{Files, Path, Paths}
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Codec
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object WorkerSrv {

  /** Attributes inherited from the source worker when it is duplicated, unless the caller overrides them */
  private val inheritedAttributes: Seq[String] = Seq("description", "dataTypeList", "rate", "rateUnit", "jobCache", "jobTimeout")

  /** Upper bound for the name of a duplicated worker (stored as an Elasticsearch keyword, shown in TheHive menus) */
  val maxNameLength: Int = 128

  /**
    * Build the creation fields of a copy of a worker.
    * @param source    raw attributes of the worker to copy (as stored in the database)
    * @param overrides fields provided by the caller: "name" is mandatory and must differ from the source name;
    *                  "description", "configuration" and the options are optional and default to the source values.
    *                  Configuration keys absent from the override are inherited from the source configuration.
    */
  def duplicateFields(source: JsObject, overrides: Fields): Try[Fields] = {
    val sourceName   = (source \ "name").asOpt[String]
    val sourceConfig = (source \ "configuration").asOpt[String].flatMap(c => Try(Json.parse(c).as[JsObject]).toOption).getOrElse(JsObject.empty)
    // A blank description means "keep the source description"
    val cleanOverrides = overrides.getString("description").filter(_.trim.isEmpty).fold(overrides)(_ => overrides.unset("description"))
    val overrideConfig: Try[JsObject] = overrides.getValue("configuration") match {
      case None | Some(JsNull) => Success(JsObject.empty)
      case Some(cfg: JsObject) => Success(cfg)
      case Some(_)             => Failure(BadRequestError("The configuration of a duplicated worker must be a JSON object"))
    }

    overrideConfig.flatMap { overrideConfig =>
      overrides.getString("name").map(_.trim).filter(_.nonEmpty) match {
        case None => Failure(MissingAttributeError("name"))
        case Some(name) if name.length > maxNameLength =>
          Failure(BadRequestError(s"The worker name must not exceed $maxNameLength characters"))
        case Some(name) if sourceName.contains(name) =>
          Failure(BadRequestError(s"The duplicated worker must have a name different from the original one ($name)"))
        case Some(name) =>
          val fields = inheritedAttributes.foldLeft(cleanOverrides.set("name", name)) { (fields, attribute) =>
            (source \ attribute).toOption.filterNot(_ == JsNull).fold(fields)(value => fields.addIfAbsent(attribute, value))
          }
          Success(fields.set("configuration", sourceConfig.deepMerge(overrideConfig)))
      }
    }
  }
}

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
  private val updateDockerImage: Boolean = config.get[Boolean]("worker.updateDockerImage")

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
        case w if w.dockerImage.isDefined && jobRunnerSrv.k8sRunnerIsEnable    => true
        case w =>
          val reason =
            if (w.command.isDefined) "process runner is disabled"
            else if (w.dockerImage.isDefined && !jobRunnerSrv.dockerRunnerIsEnable)
              "Docker runner is disabled"
            else if (w.dockerImage.isDefined && !jobRunnerSrv.k8sRunnerIsEnable)
              "Kubernetes runner is disabled"
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
        workerMapLock.synchronized {
          workerMap = wmap
        }
        logger.info(s"New worker list:\n\n\t${workerMap.values.map(a => s"${a.name} ${a.version}").mkString("\n\t")}\n")
        if (updateDockerImage) {
          userSrv.inInitAuthContext { implicit authContext =>
            updateWorkerDockerImage
          }
        }
      }

  }

  def create(organization: Organization, workerDefinition: WorkerDefinition, workerFields: Fields)(
      implicit
      authContext: AuthContext
  ): Future[Worker] = {
    val rawConfig = workerFields.getValue("configuration").fold(JsObject.empty)(_.as[JsObject])
    val configItems = workerDefinition.configurationItems ++ BaseConfig.global(workerDefinition.tpe, config).items ++ BaseConfig
      .tlp
      .items ++ BaseConfig.pap.items
    val configOrErrors = configItems
      .validatedBy(_.read(rawConfig))
      .map(JsObject.apply)

    val unknownConfigItems = (rawConfig.value.keySet.toSet -- configItems.map(_.name))
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
          ).recoverWith {
            case _: ConflictError =>
              val name = workerFields.getString("name").getOrElse(workerDefinition.name)
              Future.failed(ConflictError(s"A ${workerDefinition.tpe} named \"$name\" already exists in this organization", Json.obj("name" -> name)))
          }, {
          case One(e)         => Future.failed(e)
          case Every(es @ _*) => Future.failed(AttributeCheckingError(s"worker(${workerDefinition.name}).configuration", es))
          case x              => Future.failed(UnknownAttributeError(s"$x", Json.obj()))
        }
      )
  }

  def create(organizationId: String, workerDefinition: WorkerDefinition, workerFields: Fields)(implicit authContext: AuthContext): Future[Worker] =
    for {
      organization <- organizationSrv.get(organizationId)
      worker       <- create(organization, workerDefinition, workerFields)
    } yield worker

  /**
    * Create a copy of an existing worker in the same organization. The copy is backed by the same worker
    * definition (hence the same docker image or command) but has its own name, description, configuration
    * and options. See [[WorkerSrv.duplicateFields]] for the accepted fields.
    */
  def duplicate(worker: Worker, fields: Fields)(implicit authContext: AuthContext): Future[Worker] =
    for {
      duplicateFields <- Future.fromTry(WorkerSrv.duplicateFields(worker.attributes, fields))
      workerDefinition <- Future.fromTry(
        getDefinition(worker.workerDefinitionId()).recoverWith {
          case _: NotFoundError =>
            Failure(NotFoundError(s"Worker definition ${worker.workerDefinitionId()} is not in the catalog, an obsolete worker can't be duplicated"))
        }
      )
      organization <- organizationSrv.get(worker.organization)
      // create() always stores the definition's description; carry the requested one through a definition copy
      description = duplicateFields.getString("description").getOrElse(workerDefinition.description)
      duplicated <- create(organization, workerDefinition.copy(description = description), duplicateFields)
    } yield duplicated

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

  def updateWorkerDockerImage(implicit authContext: AuthContext): Future[Done] = {
    import org.elastic4play.services.QueryDSL._
    find(contains("dockerImage"), None, Nil)
      ._1
      .mapAsync(3) { worker =>
        val done = for {
          workerDefinition <- workerMap.get(worker.workerDefinitionId())
          newDockerImage   <- workerDefinition.dockerImage
          oldDockerImage   <- worker.dockerImage()
          if oldDockerImage != newDockerImage
        } yield update(worker, Fields.empty.set("dockerImage", newDockerImage)).map(_ => ())
        done.getOrElse(Future.successful(())).recoverWith {
          case error =>
            logger.warn(s"Unable to update docker image of worker ${worker.workerDefinitionId}", error)
            Future.failed(error)
        }
      }
      .run()
  }
}
