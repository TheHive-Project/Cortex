package org.elastic4play.database

import java.util.{Map => JMap}

import org.elastic4play.models.BaseEntity
import org.junit.runner.RunWith
import org.specs2.matcher.ValueCheck.typedValueCheck
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsArray, JsNull, Json}
import play.api.test.PlaySpecification

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class DBModifySpec extends PlaySpecification with Mockito {

  "DBModify" should {
    "build correct update script" in {
      val db       = mock[DBConfiguration]
      val dbmodify = new DBModify(db)
      val attributes = Json.obj(
        "obj"             -> Json.obj("subAttr1" -> 1),
        "arr"             -> Seq("a", "b", "c"),
        "num"             -> 42,
        "str"             -> "blah",
        "bool"            -> false,
        "sub.attr.str"    -> "subValue",
        "n"               -> JsNull,
        "sub.attr.remove" -> JsArray(),
        "remove"          -> JsArray()
      )
      val script = dbmodify.buildScript(mock[BaseEntity], attributes)

      script.script must_=== """
        ctx._source["obj"]=params.param0;
        ctx._source["arr"]=params.param1;
        ctx._source["num"]=params.param2;
        ctx._source["str"]=params.param3;
        ctx._source["bool"]=params.param4;
        ctx._source["sub"]["attr"]["str"]=params.param5;
        ctx._source["n"]=null;
        ctx._source["sub"]["attr"].remove("remove");
        ctx._source.remove("remove")""".filterNot(c => "\n ".contains(c))
      script.params - "param0" - "param1" must_=== Map("param2"                              -> 42, "param3" -> "blah", "param4" -> false, "param5" -> "subValue")
      mapAsScalaMap(script.params("param0").asInstanceOf[JMap[_, _]]) must_== Map("subAttr1" -> 1)
      script.params("param1").asInstanceOf[Array[Any]].toSeq must contain(exactly[Any]("a", "b", "c"))
    }
  }
}
