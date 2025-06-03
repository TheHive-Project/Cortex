package org.elastic4play.database

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import javax.inject.{Inject, Singleton}
import org.elastic4play.models.BaseEntity
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class DBRemove @Inject() (db: DBConfiguration) {

  lazy val logger: Logger = Logger(getClass)

  def apply(entity: BaseEntity)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug(s"Remove ${entity.model.modelName} ${entity.id}")
    db.execute {
        deleteById(db.indexName, entity.id)
          .routing(entity.routing)
          .refresh(RefreshPolicy.WAIT_FOR)
      }
      .transform(r => Success(r.isSuccess))
  }
}
