//package org.elastic4play.database
//
//import scala.concurrent.ExecutionContext.Implicits.{ global ⇒ ec }
//import scala.concurrent.Future
//
//import play.api.libs.json.Json.toJsFieldJsValueWrapper
//import play.api.libs.json.{ JsNull, Json }
//import play.api.test.PlaySpecification
//
//import com.sksamuel.elastic4s.{ RichSearchHit, RichSearchResponse, SearchDefinition }
//import org.junit.runner.RunWith
//import org.specs2.mock.Mockito
//import org.specs2.runner.JUnitRunner
//
//import org.elastic4play.utils.RichFuture
//
//@RunWith(classOf[JUnitRunner])
//class DBGetSpec extends PlaySpecification with Mockito {
//
//  "DBGet" should {
//    "retrieve document" in {
//      val db = mock[DBConfiguration]
//      val dbget = new DBGet(db, ec)
//      db.indexName returns "testIndex"
//      val modelName = "user"
//      val entityId = "me"
//
//      val searchDefinition = capture[SearchDefinition]
//      val response = mock[RichSearchResponse]
//      val searchHit = mock[RichSearchHit]
//      response.hits returns Array(searchHit)
//      searchHit.id returns entityId
//      searchHit.`type` returns modelName
//      searchHit.fields returns Map.empty
//
//      db.execute(searchDefinition.capture) returns Future.successful(response)
//      dbget(modelName, entityId).await must_== Json.obj(
//        "_type" → modelName,
//        "_routing" → entityId,
//        "_parent" → JsNull,
//        "_id" → entityId)
//
//      Json.parse(searchDefinition.value._builder.toString) must_== Json.obj(
//        "query" → Json.obj(
//          "ids" → Json.obj(
//            "type" → "user",
//            "values" → Seq("me"))),
//        "fields" → Seq("_source", "_routing", "_parent"))
//    }
//  }
//}
