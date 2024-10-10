package org.elastic4play.services

import java.util.{Date, UUID}

import org.elastic4play.controllers.JsonInputValue
import org.elastic4play.database.DBModify
import org.elastic4play.models.{Attribute, EntityDef, ModelDef, AttributeFormat => F}
import org.elastic4play.utils.RichFuture
import org.elastic4play.{AttributeCheckingError, InvalidFormatAttributeError, UnknownAttributeError}
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner
import play.api.libs.json._
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class UpdateSrvSpec extends PlaySpecification with Mockito {

  class TestModel extends ModelDef[TestModel, TestEntity]("testModel", "TestModel", "/test") {
    val textAttribute: Attribute[String]      = attribute("textAttribute", F.textFmt, "textAttribute")
    val stringAttribute: Attribute[String]    = attribute("stringAttribute", F.stringFmt, "stringAttribute")
    val dateAttribute: Attribute[Date]        = attribute("dateAttribute", F.dateFmt, "dateAttribute")
    val booleanAttribute: Attribute[Boolean]  = attribute("booleanAttribute", F.booleanFmt, "booleanAttribute")
    val uuidAttribute: Attribute[UUID]        = attribute("uuidAttribute", F.uuidFmt, "uuidAttribute")
    val hashAttribute: Attribute[String]      = attribute("hashAttribute", F.hashFmt, "hashAttribute")
    val metricAttribute: Attribute[JsValue]   = attribute("metricAttribute", F.metricsFmt, "metricAttribute")
    val multiAttibute: Attribute[Seq[String]] = multiAttribute("multiAttribute", F.stringFmt, "multiAttribute")
  }
  class TestEntity(model: TestModel, attributes: JsObject) extends EntityDef[TestModel, TestEntity](model, attributes)
  val fieldsSrv: FieldsSrv         = mock[FieldsSrv]
  val dbModify: DBModify           = mock[DBModify]
  val eventSrv: EventSrv           = mock[EventSrv]
  val getSrv: GetSrv               = mock[GetSrv]
  val attachmentSrv: AttachmentSrv = mock[AttachmentSrv]
  val updateSrv                    = new UpdateSrv(fieldsSrv, dbModify, getSrv, attachmentSrv, eventSrv)
  val model                        = new TestModel

  "UpdateSrv.checkAttributes" should {
    "return attributes if there is correct" in {
      val attrs = Json.obj(
        "textAttribute"           -> "valid text",
        "stringAttribute"         -> "valid string",
        "dateAttribute"           -> "20160128T175800+0100",
        "booleanAttribute"        -> true,
        "uuidAttribute"           -> "ee0caf69-560b-4453-9bae-72982225e661",
        "hashAttribute"           -> "01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b",
        "metricAttribute"         -> Json.obj("metric1" -> 1, "metric2" -> 2),
        "metricAttribute.metric3" -> 3
      )

      updateSrv.checkAttributes(attrs, model).await must_== attrs
    }

    "returns errors if attribute format is invalid" in {
      val attrs = Json.obj(
        "textAttribute" -> true,
        // "stringAttribute" -> 2134,
        "dateAttribute"           -> "2016-01-28",
        "booleanAttribute"        -> "true",
        "uuidAttribute"           -> "ee0caf69560b44539bae72982225e661",
        "hashAttribute"           -> "01ba471-invalid-9c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b",
        "metricAttribute"         -> Json.obj("metric1" -> "blah", "metric2" -> 2),
        "unknownAttribute"        -> 1,
        "metricAttribute.metric3" -> "aze",
        "multiAttribute"          -> "single value"
      )

      updateSrv.checkAttributes(attrs, model).await must throwA[AttributeCheckingError].like {
        case AttributeCheckingError(_, errors) =>
          errors must contain( //exactly[AttributeError](
            InvalidFormatAttributeError("textAttribute", model.textAttribute.format.name, JsonInputValue(JsBoolean(true))),
            InvalidFormatAttributeError("dateAttribute", model.dateAttribute.format.name, JsonInputValue(JsString("2016-01-28"))),
            InvalidFormatAttributeError("booleanAttribute", model.booleanAttribute.format.name, JsonInputValue(JsString("true"))),
            InvalidFormatAttributeError(
              "uuidAttribute",
              model.uuidAttribute.format.name,
              JsonInputValue(JsString("ee0caf69560b44539bae72982225e661"))
            ),
            InvalidFormatAttributeError(
              "hashAttribute",
              model.hashAttribute.format.name,
              JsonInputValue(JsString("01ba471-invalid-9c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b"))
            ),
            InvalidFormatAttributeError(
              "metricAttribute",
              model.metricAttribute.format.name,
              JsonInputValue(Json.obj("metric1" -> "blah", "metric2" -> 2))
            ),
            UnknownAttributeError("unknownAttribute", JsNumber(1)),
            InvalidFormatAttributeError("metricAttribute", "number", JsonInputValue(JsString("aze"))),
            InvalidFormatAttributeError("multiAttribute", "multi-string", JsonInputValue(JsString("single value")))
          )
      }
    }
  }

}
