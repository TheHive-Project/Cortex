package org.elastic4play.controllers

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import play.api.mvc._

import org.elastic4play.Timed
import org.elastic4play.services.MigrationSrv

/**
  * Migration controller : start migration process
  */
@Singleton
class MigrationCtrl @Inject() (migrationSrv: MigrationSrv, components: ControllerComponents, implicit val ec: ExecutionContext)
    extends AbstractController(components) {

  @Timed("controllers.MigrationCtrl.migrate")
  def migrate: Action[AnyContent] = Action.async {
    migrationSrv.migrate.map(_ => NoContent)
  }
}
