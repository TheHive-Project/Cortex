package org.thp.cortex.services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.json.JsObject
import akka.NotUsed
import akka.stream.scaladsl.Source
import org.thp.cortex.models.{Organization, OrganizationModel}
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._

@Singleton
class OrganizationSrv(
    cacheExpiration: Duration,
    organizationModel: OrganizationModel,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    deleteSrv: DeleteSrv,
    createSrv: CreateSrv,
    cache: AsyncCacheApi
) {

  @Inject() def this(
      config: Configuration,
      organizationModel: OrganizationModel,
      getSrv: GetSrv,
      updateSrv: UpdateSrv,
      findSrv: FindSrv,
      deleteSrv: DeleteSrv,
      createSrv: CreateSrv,
      cache: AsyncCacheApi
  ) =
    this(config.get[Duration]("cache.organization"), organizationModel, getSrv, updateSrv, findSrv, deleteSrv, createSrv, cache)

  def create(fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Organization] =
    createSrv[OrganizationModel, Organization](organizationModel, fields)

  def get(orgId: String)(implicit ec: ExecutionContext): Future[Organization] = cache.getOrElseUpdate(s"org-$orgId", cacheExpiration) {
    getSrv[OrganizationModel, Organization](organizationModel, orgId)
  }

  def update(orgId: String, fields: Fields)(implicit Context: AuthContext, ec: ExecutionContext): Future[Organization] =
    update(orgId, fields, ModifyConfig.default)

  def update(orgId: String, fields: Fields, modifyConfig: ModifyConfig)(implicit Context: AuthContext, ec: ExecutionContext): Future[Organization] = {
    cache.remove(s"org-$orgId")
    updateSrv[OrganizationModel, Organization](organizationModel, orgId, fields, modifyConfig)
  }

  def update(organization: Organization, fields: Fields)(implicit Context: AuthContext, ec: ExecutionContext): Future[Organization] =
    update(organization, fields, ModifyConfig.default)

  def update(organization: Organization, fields: Fields, modifyConfig: ModifyConfig)(
      implicit Context: AuthContext,
      ec: ExecutionContext
  ): Future[Organization] = {
    cache.remove(s"org-${organization.id}")
    updateSrv(organization, fields, modifyConfig)
  }

  def delete(orgId: String)(implicit Context: AuthContext, ec: ExecutionContext): Future[Organization] = {
    cache.remove(s"org-$orgId")
    deleteSrv[OrganizationModel, Organization](organizationModel, orgId)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String])(
      implicit ec: ExecutionContext
  ): (Source[Organization, NotUsed], Future[Long]) =
    findSrv[OrganizationModel, Organization](organizationModel, queryDef, range, sortBy)

  def stats(queryDef: QueryDef, aggs: Seq[Agg])(implicit ec: ExecutionContext): Future[JsObject] = findSrv(organizationModel, queryDef, aggs: _*)
}
