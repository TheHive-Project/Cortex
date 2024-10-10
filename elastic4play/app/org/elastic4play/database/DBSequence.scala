package org.elastic4play.database

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import javax.inject.{Inject, Singleton}
import org.elastic4play.models.{Attribute, ModelAttributes, AttributeFormat => F, AttributeOption => O}

import scala.concurrent.{ExecutionContext, Future}

class SequenceModel extends ModelAttributes("sequence") {
  val counter: Attribute[Long] = attribute("sequenceCounter", F.numberFmt, "Value of the sequence", O.model)
}

@Singleton
class DBSequence @Inject() (db: DBConfiguration) {

  def apply(seqId: String)(implicit ec: ExecutionContext): Future[Int] =
    db.execute {
      updateById(db.indexName, s"sequence_$seqId")
        .upsert("sequenceCounter" -> 1, "relations" -> "sequence")
        .script("ctx._source.sequenceCounter += 1")
        .retryOnConflict(5)
        .fetchSource(true)
        .refresh(RefreshPolicy.WAIT_FOR)
    } map { updateResponse =>
      updateResponse.source("sequenceCounter").asInstanceOf[Int]
    }
}
