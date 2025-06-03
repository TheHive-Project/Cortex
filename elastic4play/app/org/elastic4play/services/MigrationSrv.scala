package org.elastic4play.services

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.ElasticDsl._
import javax.inject.{Inject, Singleton}
import org.elastic4play.InternalError
import org.elastic4play.database._
import play.api.Logger
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class MigrationEvent(modelName: String, current: Long, total: Long) extends EventMessage

case object EndOfMigrationEvent extends EventMessage

trait MigrationOperations {
  val operations: PartialFunction[DatabaseState, Seq[Operation]]
  def beginMigration(version: Int): Future[Unit]
  def endMigration(version: Int): Future[Unit]
}

/* DatabaseState is the state of a specific version of the database.
 * States are linked from last present version to the desired version
 */
abstract class DatabaseState {
  def version: Int
  def source(tableName: String): Source[JsObject, NotUsed]
  def count(tableName: String): Future[Long]
  def getEntity(tableName: String, id: String): Future[JsObject]
}

object DatabaseState {
  def unapply(s: DatabaseState): Option[Int] = Some(s.version)
}

@Singleton
class MigrationSrv @Inject() (
    migration: MigrationOperations,
    db: DBConfiguration,
    dbcreate: DBCreate,
    dbfind: DBFind,
    dbindex: DBIndex,
    modelSrv: ModelSrv,
    eventSrv: EventSrv,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  private[MigrationSrv] lazy val logger = Logger(getClass)

  /* Constructed state of the database from the previous version */
  class MigrationTransition(db: DBConfiguration, previousState: DatabaseState, operations: Seq[Operation]) extends DatabaseState {
    override def version: Int                                         = db.version
    override def source(tableName: String): Source[JsObject, NotUsed] = operations.foldLeft(previousState.source _)((f, op) => op(f))(tableName)
    override def count(tableName: String): Future[Long]               = previousState.count(tableName)
    override def getEntity(tableName: String, id: String): Future[JsObject] =
      previousState.getEntity(tableName, id).flatMap { previousValue =>
        operations
          .foldLeft((_: String) => Source.single(previousValue))((f, op) => op(f))(tableName)
          .runWith(Sink.head)
      }
  }

  /* Last version of database */
  class OriginState(db: DBConfiguration) extends DatabaseState {
    private val currentdbfind     = dbfind.switchTo(db)
    private lazy val currentdbget = new DBGet(db)
    override def version: Int     = db.version
    override def source(tableName: String): Source[JsObject, NotUsed] =
      currentdbfind.apply(Some("all"), Nil)(indexName => search(indexName).matchQuery("relations", tableName))._1
    override def count(tableName: String): Future[Long]                           = new DBIndex(db, 0, 0, Map.empty).getSize(tableName)
    override def getEntity(tableName: String, entityId: String): Future[JsObject] = currentdbget(tableName, entityId)
  }

  /* If there is no database, use empty one */
  object EmptyState extends DatabaseState {
    override def version                                                    = 1
    override def source(tableName: String): Source[JsObject, NotUsed]       = Source.empty[JsObject]
    override def count(tableName: String): Future[Long]                     = Future.successful(0)
    override def getEntity(tableName: String, id: String): Future[JsObject] = Future.failed(new Exception("TODO"))
  }

  def migrationPath(db: DBConfiguration): Future[(Int, DatabaseState)] =
    new DBIndex(db, 0, 0, Map.empty).getIndexStatus.flatMap {
      case true =>
        logger.info(s"Initiate database migration from version ${db.version}")
        Future.successful(db.version -> new OriginState(db))
      case false if db.version == 1 =>
        logger.info("Create a new empty database")
        Future.successful(0 -> EmptyState)
      case false =>
        migrationPath(db.previousVersion).map {
          case (v, s) =>
            logger.info(s"Migrate database from version $v, add operations for version ${db.version}")
            val operations = migration
              .operations
              .applyOrElse(s, (_: DatabaseState) => throw InternalError(s"No operation for version ${s.version}, migration impossible"))
            v -> new MigrationTransition(db, s, operations)
        }
    }

  def migrationEvent(modelName: String, current: Long, total: Long): JsObject =
    Json.obj("objectType" -> "migration", "rootId" -> "none", "tableName" -> modelName, "current" -> current, "total" -> total)

  def migrateEntities(modelName: String, entities: Source[JsObject, _], total: Long): Future[Unit] = {
    val count = Source.fromIterator(() => Iterator.from(1))
    val r = entities
      .zipWith(count) { (entity, current) =>
        eventSrv.publish(MigrationEvent(modelName, current.toLong, total))
        (entity \ "_type").asOpt[JsString].fold(entity) { t =>
          val relations = (entity \ "_parent").asOpt[JsString].fold[JsValue](t)(p => Json.obj("name" -> t, "parent" -> p))
          entity - "_type" - "_parent" + ("relations" -> relations)
        }
      }
      .runWith(dbcreate.sink())
    r.onComplete { x =>
      println(s"migrateEntity($modelName) has finished : $x")
    }
    r
  }

  def migrateTable(mig: DatabaseState, table: String): Future[Unit] =
    mig
      .count(table)
      .flatMap { total =>
        logger.info(s"Migrating $total entities from $table")
        migrateEntities(table, mig.source(table), total)
      }

  private var migrationProcess = Future.successful(())

  def migrate: Future[Unit] = {
    if (!dbindex.indexStatus && migrationProcess.isCompleted) {
      val models = modelSrv.list
      migrationProcess = migrationPath(db)
        .flatMap { mig =>
          dbindex.createIndex(new SequenceModel +: models).map(_ => mig)
        }
        .flatMap { versionMig =>
          migration.beginMigration(versionMig._1).map(_ => versionMig)
        }
        // for all tables, get entities from migrationPath and insert in current database
        .flatMap {
          case (version, mig) =>
            Future
              .sequence(
                ("sequence" +: models.map(_.modelName).sorted)
                  .distinct
                  .map(t =>
                    migrateTable(mig, t).recover {
                      case error => logger.error(s"Migration of table $t failed :", error)
                    }
                  )
              )
              .flatMap(_ => migration.endMigration(version))
        }
      migrationProcess.onComplete {
        case Success(_) =>
          logger.info("End of migration")
          eventSrv.publish(EndOfMigrationEvent)
        case Failure(t) =>
          logger.error("Migration fail", t)
          Future.failed(t)
      }
    }
    migrationProcess
  }

  def isMigrating: Boolean = !migrationProcess.isCompleted
  def isReady: Boolean     = dbindex.indexStatus && migrationProcess.isCompleted
}

/* Operation applied to the previous state of the database to get next version */
trait Operation extends ((String => Source[JsObject, NotUsed]) => (String => Source[JsObject, NotUsed]))

object Operation {

  def apply(o: (String => Source[JsObject, NotUsed]) => String => Source[JsObject, NotUsed]): Operation =
    (f: String => Source[JsObject, NotUsed]) => o(f)

  def renameEntity(previous: String, next: String): Operation =
    Operation((f: String => Source[JsObject, NotUsed]) => {
      case `next` => f(previous).map(_ + ("_type" -> JsString(next)))
      case "audit" =>
        f("audit").map { x =>
          (x \ "objectType").asOpt[String] match {
            case Some(`previous`) => x - "objectType" + ("objectType" -> JsString(next))
            case _                => x
          }
        }
      case other => f(other)
    })

  def mapEntity(tableFilter: String => Boolean, transform: JsObject => JsObject): Operation =
    Operation((f: String => Source[JsObject, NotUsed]) => {
      case table if tableFilter(table) => f(table).map(transform)
      case other                       => f(other)
    })

  def mapEntity(tables: String*)(transform: JsObject => JsObject): Operation = mapEntity(tables.contains, transform)

  def apply(table: String)(transform: JsObject => JsObject): Operation = mapEntity(_ == table, transform)

  def removeEntity(tableFilter: String => Boolean, filter: JsObject => Boolean): Operation =
    Operation((f: String => Source[JsObject, NotUsed]) => {
      case table if tableFilter(table) => f(table).filterNot(filter)
      case other                       => f(other)
    })

  def removeEntity(tables: String*)(filter: JsObject => Boolean): Operation = removeEntity(tables.contains, filter)

  def removeEntity(table: String)(filter: JsObject => Boolean): Operation = removeEntity(_ == table, filter)

  def renameAttribute(tableFilter: String => Boolean, newName: String, oldNamePath: Seq[String]): Operation =
    Operation((f: String => Source[JsObject, NotUsed]) => {
      // rename attribute in the selected entities
      case table if tableFilter(table) =>
        f(table).map { o =>
          rename(o, newName, oldNamePath)
        }
      case "audit" => f("audit").map(o => rename(o, newName, "details" +: oldNamePath))
      case other   => f(other)
    })

  def renameAttribute(tables: Seq[String], newName: String, oldNamePath: String*): Operation =
    renameAttribute(a => tables.contains(a), newName, oldNamePath)

  def renameAttribute(table: String, newName: String, oldNamePath: String*): Operation = renameAttribute(_ == table, newName, oldNamePath)

  def rename(value: JsObject, newName: String, path: Seq[String]): JsObject =
    if (path.isEmpty) {
      value
    } else {
      val head = path.head
      val tail = path.tail
      value \ head match {
        case JsDefined(v) if tail.isEmpty => value - head + (newName -> v)
        case JsDefined(v: JsObject)       => value - head + (head    -> rename(v, newName, tail))
        case _                            => value
      }
    }

  def mapAttribute(tableFilter: String => Boolean, attribute: String, transform: JsValue => JsValue): Operation =
    mapEntity(
      tableFilter,
      x =>
        x \ attribute match {
          case _: JsUndefined => x
          case JsDefined(a)   => x + (attribute -> transform(a))
        }
    )

  def mapAttribute(tables: Seq[String], attribute: String)(transform: JsValue => JsValue): Operation =
    mapAttribute(a => tables.contains(a), attribute, transform)

  def mapAttribute(table: String, attribute: String)(transform: JsValue => JsValue): Operation = mapAttribute(_ == table, attribute, transform)

  def removeAttribute(tableFilter: String => Boolean, attributes: String*): Operation =
    mapEntity(
      tableFilter,
      x =>
        attributes.foldLeft(x) { (y, a) =>
          y - a
        }
    )

  def removeAttribute(tables: Seq[String], attributes: String*): Operation = removeAttribute(a => tables.contains(a), attributes: _*)

  def removeAttribute(table: String, attributes: String*): Operation = removeAttribute(_ == table, attributes: _*)

  def addAttribute(tableFilter: String => Boolean, attributes: (String, JsValue)*): Operation =
    mapEntity(
      tableFilter,
      x =>
        attributes.foldLeft(x) { (y, a) =>
          y + a
        }
    )

  def addAttribute(tables: Seq[String], attributes: (String, JsValue)*): Operation = addAttribute(t => tables.contains(t), attributes: _*)

  def addAttribute(table: String, attributes: (String, JsValue)*): Operation = addAttribute(_ == table, attributes: _*)

  def addAttributeIfAbsent(tableFilter: String => Boolean, attributes: (String, JsValue)*): Operation =
    mapEntity(
      tableFilter,
      x =>
        attributes.foldLeft(x) { (y, a) =>
          y \ a._1 match {
            case _: JsUndefined => x + a
            case _              => x
          }
        }
    )

  def addAttributeIfAbsent(tables: Seq[String], attributes: (String, JsValue)*): Operation = addAttribute(t => tables.contains(t), attributes: _*)

  def addAttributeIfAbsent(table: String, attributes: (String, JsValue)*): Operation = addAttribute(_ == table, attributes: _*)
}
