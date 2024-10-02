//package org.elastic4play.database
//
//import scala.concurrent.ExecutionContext.Implicits.{global ⇒ ec}
//import scala.concurrent.Future
//
//import play.api.libs.json.{JsObject, JsString, Json}
//import play.api.test.PlaySpecification
//
//import com.sksamuel.elastic4s.http.index.IndexResponse
//import com.sksamuel.elastic4s.http.ElasticDsl.IndexHandler
//import com.sksamuel.elastic4s.indexes.IndexRequest
//import common.{Fabricator ⇒ F}
//import org.junit.runner.RunWith
//import org.specs2.mock.Mockito
//import org.specs2.runner.JUnitRunner
//
//import org.elastic4play.models.BaseEntity
//import org.elastic4play.utils._
//
//@RunWith(classOf[JUnitRunner])
//class DBCreateSpec extends PlaySpecification with Mockito {
//  val modelName: String       = F.string("modelName")
//  val defaultEntityId: String = F.string("defaultEntityId")
//  val sampleDoc: JsObject     = Json.obj("caseId" → 42, "title" → "Test case", "description" → "Case used for unit test", "tags" → Seq("test", "specs"))
//
//  class DBCreateWrapper {
//    val db: DBConfiguration = mock[DBConfiguration]
//    val dbcreate            = new DBCreate(db, ec)
//
//    def apply(modelName: String, attributes: JsObject): (JsObject, IndexRequest) = {
//      val indexResponse = mock[IndexResponse]
//      indexResponse.id returns (attributes \ "_id").asOpt[String].getOrElse(defaultEntityId)
//      db.execute(any[IndexRequest]) returns Future.successful(indexResponse)
//      val attrs  = dbcreate(modelName, attributes).await
//      val captor = capture[IndexRequest]
//      there was one(db).execute(captor.capture)
//      (attrs, captor.value)
//    }
//
//    def apply(parent: BaseEntity, attributes: JsObject): (JsObject, IndexRequest) = {
//      val indexResponse = mock[IndexResponse]
//      indexResponse.id returns (attributes \ "_id").asOpt[String].getOrElse(defaultEntityId)
//      db.execute(any[IndexRequest]) returns Future.successful(indexResponse)
//      val attrs  = dbcreate(modelName, Some(parent), attributes).await
//      val captor = capture[IndexRequest]
//      there was one(db).execute(captor.capture)
//      (attrs, captor.value)
//    }
//  }
//
//  "DBCreate" should {
//    "create document without id, parent or routing" in {
//      val dbcreate                = new DBCreateWrapper
//      val (returnAttrs, indexDef) = dbcreate(modelName, sampleDoc)
//      (returnAttrs \ "_type").asOpt[String] must beSome(modelName)
//      (returnAttrs \ "_id").asOpt[String] must beSome(defaultEntityId)
//      (returnAttrs \ "_routing").asOpt[String] must beSome(defaultEntityId)
//      (returnAttrs \ "_parent").asOpt[String] must beNone
//      indexDef.id must beNone
//      indexDef.parent must beNone
//      indexDef.routing must beNone
//    }
//
//    "create document with id, parent and routing" in {
//      val entityId = F.string("entityId")
//      val routing  = F.string("routing")
//      val parentId = F.string("parentId")
//      val dbcreate = new DBCreateWrapper()
//      val (returnAttrs, indexDef) = dbcreate(
//        modelName,
//        sampleDoc +
//          ("_id"      → JsString(entityId)) +
//          ("_routing" → JsString(routing)) +
//          ("_parent"  → JsString(parentId))
//      )
//
//      (returnAttrs \ "_type").asOpt[String] must beSome(modelName)
//      (returnAttrs \ "_id").asOpt[String] must beSome(entityId)
//      (returnAttrs \ "_routing").asOpt[String] must beSome(routing)
//      (returnAttrs \ "_parent").asOpt[String] must beSome(parentId)
//      indexDef.id must beSome(entityId)
//      indexDef.parent must beSome(parentId)
//      indexDef.routing must beSome(routing)
//    }
//
//    "create document with id and parent entity" in {
//      val entityId = F.string("entityId")
//      val routing  = F.string("routing")
//      val parentId = F.string("parentId")
//
//      val dbcreate = new DBCreateWrapper()
//      val parent   = mock[BaseEntity]
//      parent.id returns parentId
//      parent.routing returns routing
//      val (returnAttrs, indexDef) = dbcreate(parent, sampleDoc + ("_id" → JsString(entityId)))
//
//      (returnAttrs \ "_type").asOpt[String] must beSome(modelName)
//      (returnAttrs \ "_id").asOpt[String] must beSome(entityId)
//      (returnAttrs \ "_routing").asOpt[String] must beSome(routing)
//      (returnAttrs \ "_parent").asOpt[String] must beSome(parentId)
//      indexDef.id must beSome(entityId)
//      indexDef.parent must beSome(parentId)
//      indexDef.routing must beSome(routing)
//    }
//  }
//}
