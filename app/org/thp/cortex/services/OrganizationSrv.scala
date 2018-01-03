package org.thp.cortex.services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.thp.cortex.models.{ Organization, OrganizationModel }

import org.elastic4play.controllers.Fields
import org.elastic4play.services._

@Singleton
class OrganizationSrv @Inject() (
    organizationModel: OrganizationModel,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    deleteSrv: DeleteSrv,
    createSrv: CreateSrv) {

  def create(fields: Fields)(implicit authContext: AuthContext): Future[Organization] = {
    createSrv[OrganizationModel, Organization](organizationModel, fields)
  }

  def get(id: String): Future[Organization] = getSrv[OrganizationModel, Organization](organizationModel, id)

  def update(id: String, fields: Fields)(implicit Context: AuthContext): Future[Organization] = {
    updateSrv[OrganizationModel, Organization](organizationModel, id, fields)
  }

  def update(Organization: Organization, fields: Fields)(implicit Context: AuthContext): Future[Organization] = {
    updateSrv(Organization, fields)
  }

  def delete(id: String)(implicit Context: AuthContext): Future[Organization] =
    deleteSrv[OrganizationModel, Organization](organizationModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Organization, NotUsed], Future[Long]) = {
    findSrv[OrganizationModel, Organization](organizationModel, queryDef, range, sortBy)
  }
}
