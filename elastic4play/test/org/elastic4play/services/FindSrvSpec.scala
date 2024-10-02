//package org.elastic4play.services
//
//import play.api.libs.json.Json
//import play.api.test.PlaySpecification
//
//import com.sksamuel.elastic4s.ElasticDsl.{ matchAllQuery, search }
//import com.sksamuel.elastic4s.IndexesAndTypes.apply
//import org.junit.runner.RunWith
//import org.specs2.mock.Mockito
//import org.specs2.runner.JUnitRunner
//
//import org.elastic4play.models.BaseModelDef
//
//@RunWith(classOf[JUnitRunner])
//class FindSrvSpec extends PlaySpecification with Mockito {
//
//  val indexName = "myIndex"
//  val documentType = "myDocument"
//
//  "GroupByCategory" should {
//    "generate correct elasticsearch query" in {
//      import org.elastic4play.services.QueryDSL._
//      val catAgg = new GroupByCategory(Map(
//        "debug" → ("level" ~= "DEBUG"),
//        "info" → ("level" ~= "INFO"),
//        "warn" → ("level" ~= "WARN")), Seq(selectCount))
//
//      val query = search(indexName → documentType).matchAllQuery.aggregations(catAgg(mock[BaseModelDef]))
//
//      Json.parse(query._builder.toString) must_== Json.parse("""
//        {
//            "query": {
//                "match_all": {}
//            },
//            "aggregations": {
//                "categories": {
//                    "filters": {
//                        "filters": {
//                            "debug": { "term": { "level": "DEBUG" } },
//                            "info": { "term": { "level": "INFO" } },
//                            "warn": { "term": { "level": "WARN" } }
//                        }
//                    },
//                    "aggregations": {
//                        "count": {
//                            "filter": {
//                                "match_all": {}
//                            }
//                        }
//                    }
//                }
//            }
//        }""")
//    }
//  }
//}
