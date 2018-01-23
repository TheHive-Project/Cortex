package org.thp.cortex.services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import play.api.libs.json.JsObject

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.thp.cortex.models.{ Organization, OrganizationModel }

import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
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

  def update(id: String, fields: Fields)(implicit Context: AuthContext): Future[Organization] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit Context: AuthContext): Future[Organization] =
    updateSrv[OrganizationModel, Organization](organizationModel, id, fields, modifyConfig)

  def update(organization: Organization, fields: Fields)(implicit Context: AuthContext): Future[Organization] =
    update(organization, fields, ModifyConfig.default)

  def update(organization: Organization, fields: Fields, modifyConfig: ModifyConfig)(implicit Context: AuthContext): Future[Organization] =
    updateSrv(organization, fields, modifyConfig)

  def delete(id: String)(implicit Context: AuthContext): Future[Organization] =
    deleteSrv[OrganizationModel, Organization](organizationModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Organization, NotUsed], Future[Long]) = {
    findSrv[OrganizationModel, Organization](organizationModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(organizationModel, queryDef, aggs: _*)
}
