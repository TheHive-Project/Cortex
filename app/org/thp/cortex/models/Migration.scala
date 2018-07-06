package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

import play.api.libs.json.{ JsString, Json }

import org.thp.cortex.services.{ OrganizationSrv, UserSrv }

import org.elastic4play.controllers.Fields
import org.elastic4play.services.Operation._
import org.elastic4play.services.{ DatabaseState, MigrationOperations, Operation }

@Singleton
class Migration @Inject() (
    userSrv: UserSrv,
    organizationSrv: OrganizationSrv,
    implicit val ec: ExecutionContext) extends MigrationOperations {

  def beginMigration(version: Int): Future[Unit] = Future.successful(())

  def endMigration(version: Int): Future[Unit] = {
    userSrv.inInitAuthContext { implicit authContext ⇒
      organizationSrv.create(Fields(Json.obj(
        "name" -> "cortex",
        "description" -> "Default organization",
        "status" -> "Active")))
        .transform { case _ ⇒ Success(()) } // ignore errors (already exist)
    }
  }

  val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case DatabaseState(1) ⇒ Seq(
      // add type to analyzer
      addAttribute("analyzer", "type" -> JsString("analyzer")),

      renameAttribute("job", "workerDefinitionId", "analyzerDefinitionId"),
      renameAttribute("job", "workerId", "analyzerId"),
      renameAttribute("job", "workerName", "analyzerName"),

      renameEntity("analyzer", "worker"),
      renameAttribute("worker", "workerDefinitionId", "analyzerDefinitionId"),
      addAttribute("worker", "type" -> JsString(WorkerType.analyzer.toString)),

      renameEntity("analyzerConfig", "workerConfig"),
      addAttribute("workerConfig", "type" -> JsString(WorkerType.analyzer.toString)))

    case _ ⇒ Nil
  }
}
