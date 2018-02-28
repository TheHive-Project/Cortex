package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

import play.api.libs.json.Json

import org.thp.cortex.services.{ OrganizationSrv, UserSrv }

import org.elastic4play.controllers.Fields
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
        "name" -> "default",
        "description" -> "Default organization",
        "status" -> "Active")))
        .transform { case _ ⇒ Success(()) } // ignore errors (already exist)
    }
  }

  val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case _ ⇒ Nil
  }
}
