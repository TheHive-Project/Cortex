package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import org.elastic4play.services.{ DatabaseState, MigrationOperations, Operation }

@Singleton
class Migration @Inject() () extends MigrationOperations {
  def beginMigration(version: Int): Future[Unit] = Future.successful(())
  def endMigration(version: Int): Future[Unit] = Future.successful(())

  val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case _ ⇒ Nil
  }
}
