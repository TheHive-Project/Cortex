package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

import play.api.libs.json.{ JsString, Json }

import org.thp.cortex.services.{ OrganizationSrv, UserSrv }

import org.elastic4play.controllers.Fields
import org.elastic4play.services.Operation._
import org.elastic4play.services.{ DatabaseState, MigrationOperations, Operation }
import org.elastic4play.utils.Hasher

@Singleton
class Migration @Inject() (
    userSrv: UserSrv,
    organizationSrv: OrganizationSrv,
    implicit val ec: ExecutionContext) extends MigrationOperations {

  def beginMigration(version: Int): Future[Unit] = Future.successful(())

  def endMigration(version: Int): Future[Unit] = {
    userSrv.inInitAuthContext { implicit authContext ⇒
      organizationSrv.create(Fields(Json.obj(
        "name" → "cortex",
        "description" → "Default organization",
        "status" → "Active")))
        .transform { case _ ⇒ Success(()) } // ignore errors (already exist)
    }
  }

  val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case DatabaseState(1) ⇒
      val hasher = Hasher("MD5")
      Seq(

        // add type to analyzer
        addAttribute("analyzer", "type" → JsString("analyzer")),

        renameAttribute("job", "workerDefinitionId", "analyzerDefinitionId"),
        renameAttribute("job", "workerId", "analyzerId"),
        renameAttribute("job", "workerName", "analyzerName"),
        addAttribute("job", "type" → JsString(WorkerType.analyzer.toString)),

        addAttribute("report", "operations" → JsString("[]")),

        renameEntity("analyzer", "worker"),
        renameAttribute("worker", "workerDefinitionId", "analyzerDefinitionId"),
        addAttribute("worker", "type" → JsString(WorkerType.analyzer.toString)),
        mapEntity("worker") { worker ⇒
          val id = for {
            organizationId ← (worker \ "_parent").asOpt[String]
            name ← (worker \ "name").asOpt[String]
            tpe ← (worker \ "type").asOpt[String]
          } yield hasher.fromString(s"${organizationId}_${name}_$tpe").head.toString
          worker + ("_id" → JsString(id.getOrElse("<null>")))
        },

        renameEntity("analyzerConfig", "workerConfig"),
        addAttribute("workerConfig", "type" → JsString(WorkerType.analyzer.toString)))

    case _ ⇒ Nil
  }
}
