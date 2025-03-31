package org.elastic4play.database

import com.sksamuel.elastic4s.ElasticDsl._
import javax.inject.{Inject, Singleton}
import org.elastic4play.NotFoundError
import play.api.libs.json.JsObject

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DBGet @Inject() (db: DBConfiguration) {

  /**
    * Retrieve entities from ElasticSearch
    *
    * @param modelName the name of the model (ie. document type)
    * @param id identifier of the entity to retrieve
    * @return the entity
    */
  def apply(modelName: String, id: String)(implicit ec: ExecutionContext): Future[JsObject] =
    db.execute {
        // Search by id is not possible on child entity without routing information => id query
        search(db.indexName)
          .query(idsQuery(id) /*.types(modelName)*/ )
          .size(1)
          .seqNoPrimaryTerm(true)
      }
      .map { searchResponse =>
        searchResponse
          .hits
          .hits
          .headOption
          .fold[JsObject](throw NotFoundError(s"$modelName $id not found")) { hit =>
            DBUtils.hit2json(hit)
          }
      }
}
