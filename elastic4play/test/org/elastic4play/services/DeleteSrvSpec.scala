package org.elastic4play.services

import java.util.{Date, UUID}

import scala.concurrent.ExecutionContext.Implicits.{global => ec}
import scala.concurrent.Future
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.PlaySpecification
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner
import org.elastic4play.NotFoundError
import org.elastic4play.database.DBRemove
import org.elastic4play.models.{Attribute, EntityDef, ModelDef, AttributeFormat => F}
import org.elastic4play.utils.RichFuture

@RunWith(classOf[JUnitRunner])
class DeleteSrvSpec extends PlaySpecification with Mockito {

  class TestModel extends ModelDef[TestModel, TestEntity]("testModel", "TestModel", "/test") {
    val textAttribute: Attribute[String]     = attribute("textAttribute", F.textFmt, "textAttribute")
    val stringAttribute: Attribute[String]   = attribute("stringAttribute", F.stringFmt, "stringAttribute")
    val dateAttribute: Attribute[Date]       = attribute("dateAttribute", F.dateFmt, "dateAttribute")
    val booleanAttribute: Attribute[Boolean] = attribute("booleanAttribute", F.booleanFmt, "booleanAttribute")
    val uuidAttribute: Attribute[UUID]       = attribute("uuidAttribute", F.uuidFmt, "uuidAttribute")
    val hashAttribute: Attribute[String]     = attribute("hashAttribute", F.hashFmt, "hashAttribute")
    val metricAttribute: Attribute[JsValue]  = attribute("metricAttribute", F.metricsFmt, "metricAttribute")
  }
  class TestEntity(model: TestModel, attributes: JsObject) extends EntityDef[TestModel, TestEntity](model, attributes)

  implicit val authContext: AuthContext = mock[AuthContext]

  val model = new TestModel

  val entity = new TestEntity(
    model,
    Json.obj(
      "_id"              -> "42",
      "_routing"         -> "42",
      "_type"            -> "testModel",
      "_seqNo"           -> 1,
      "_primaryTerm"     -> 1,
      "textAttribute"    -> "valid text",
      "stringAttribute"  -> "valid string",
      "dateAttribute"    -> "20160128T175800+0100",
      "booleanAttribute" -> true,
      "uuidAttribute"    -> "ee0caf69-560b-4453-9bae-72982225e661",
      "hashAttribute"    -> "01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b",
      "metricAttribute"  -> Json.obj("metric1" -> 1, "metric2" -> 2),
      "user"             -> "testUser",
      "createdAt"        -> "20160620T162845+0200",
      "createdBy"        -> "testUser"
    )
  )

  "DeleteSrv.realDelete" should {
    "remove entity if exists" in {
      val updateSrv = mock[UpdateSrv]
      val getSrv    = mock[GetSrv]
      val dbRemove  = mock[DBRemove]
      val eventSrv  = mock[EventSrv]
      val deleteSrv = new DeleteSrv(updateSrv, getSrv, dbRemove, eventSrv)

      val id = "42"
      getSrv[TestModel, TestEntity](model, id) returns Future.successful(entity)
      dbRemove(entity) returns Future.successful(true)
      deleteSrv.realDelete[TestModel, TestEntity](model, id).await must not(throwA[Exception])
      there was one(dbRemove).apply(entity)
    }

    "returns error if entity can't be retrieve" in {
      val updateSrv = mock[UpdateSrv]
      val getSrv    = mock[GetSrv]
      val dbRemove  = mock[DBRemove]
      val eventSrv  = mock[EventSrv]
      val deleteSrv = new DeleteSrv(updateSrv, getSrv, dbRemove, eventSrv)

      val id    = "42"
      val error = NotFoundError(s"${model.modelName} $id not found")
      getSrv[TestModel, TestEntity](model, id) returns Future.failed(error)
      deleteSrv.realDelete[TestModel, TestEntity](model, id).await must throwA[NotFoundError]
    }

    "returns error if entity is not found" in {
      val updateSrv = mock[UpdateSrv]
      val getSrv    = mock[GetSrv]
      val dbRemove  = mock[DBRemove]
      val eventSrv  = mock[EventSrv]
      val deleteSrv = new DeleteSrv(updateSrv, getSrv, dbRemove, eventSrv)

      val id = "42"
      getSrv[TestModel, TestEntity](model, id) returns Future.successful(entity)
      dbRemove(entity) returns Future.successful(false)
      deleteSrv.realDelete[TestModel, TestEntity](model, id).await must throwA[NotFoundError]
    }
  }

}
