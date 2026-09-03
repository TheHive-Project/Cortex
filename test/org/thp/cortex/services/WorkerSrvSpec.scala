package org.thp.cortex.services

import org.elastic4play.controllers.Fields
import org.elastic4play.{BadRequestError, MissingAttributeError}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.libs.json.{JsNull, JsNumber, JsString, Json}
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class WorkerSrvSpec extends PlaySpecification {

  private val source = Json.obj(
    "name"               -> "Mailer_1_0",
    "description"        -> "Send an email",
    "workerDefinitionId" -> "Mailer_1_0",
    "dockerImage"        -> "cortexneurons/mailer:1",
    "dataTypeList"       -> Seq("thehive:case", "thehive:alert"),
    "configuration"      -> Json.obj("from" -> "soc@example.com", "smtp_host" -> "smtp.example.com", "check_tlp" -> true, "max_tlp" -> 2).toString,
    "rate"               -> 10,
    "rateUnit"           -> "Minute",
    "jobTimeout"         -> 30,
    "type"               -> "responder"
  )

  "WorkerSrv.duplicateFields" should {

    "require a name" in {
      WorkerSrv.duplicateFields(source, Fields.empty) must beFailedTry.withThrowable[MissingAttributeError]
      WorkerSrv.duplicateFields(source, Fields.empty.set("name", "  ")) must beFailedTry.withThrowable[MissingAttributeError]
    }

    "reject a name longer than the maximum" in {
      WorkerSrv.duplicateFields(source, Fields.empty.set("name", "x" * (WorkerSrv.maxNameLength + 1))) must beFailedTry.withThrowable[BadRequestError]
      WorkerSrv.duplicateFields(source, Fields.empty.set("name", "x" * WorkerSrv.maxNameLength)) must beSuccessfulTry
    }

    "reject the name of the original worker" in {
      WorkerSrv.duplicateFields(source, Fields.empty.set("name", "Mailer_1_0")) must beFailedTry.withThrowable[BadRequestError]
    }

    "inherit description, configuration and options from the source when not overridden" in {
      val fields = WorkerSrv.duplicateFields(source, Fields.empty.set("name", "Mailer_1_0 (SOC)")).get
      fields.getString("name") must beSome("Mailer_1_0 (SOC)")
      fields.getString("description") must beSome("Send an email")
      fields.getValue("configuration") must beSome(
        Json.obj("from" -> "soc@example.com", "smtp_host" -> "smtp.example.com", "check_tlp" -> true, "max_tlp" -> 2)
      )
      fields.getValue("dataTypeList") must beSome(Json.arr("thehive:case", "thehive:alert"))
      fields.getValue("rate") must beSome(JsNumber(10))
      fields.getString("rateUnit") must beSome("Minute")
      fields.getValue("jobTimeout") must beSome(JsNumber(30))
    }

    "not copy the identity and definition attributes of the source" in {
      val fields = WorkerSrv.duplicateFields(source, Fields.empty.set("name", "copy")).get
      fields.contains("workerDefinitionId") must beFalse
      fields.contains("dockerImage") must beFalse
      fields.contains("type") must beFalse
    }

    "apply overrides on top of the source, merging the configuration key by key" in {
      val overrides = Fields
        .empty
        .set("name", "Mailer_1_0 (IT)")
        .set("description", "Send an email to the IT team")
        .set("configuration", Json.obj("from" -> "it@example.com", "max_tlp" -> 3))
        .set("rate", JsNumber(5))
      val fields = WorkerSrv.duplicateFields(source, overrides).get
      fields.getString("description") must beSome("Send an email to the IT team")
      fields.getValue("configuration") must beSome(
        Json.obj("from" -> "it@example.com", "smtp_host" -> "smtp.example.com", "check_tlp" -> true, "max_tlp" -> 3)
      )
      fields.getValue("rate") must beSome(JsNumber(5))
      fields.getString("rateUnit") must beSome("Minute")
    }

    "fall back to the source description when the override is blank" in {
      val fields = WorkerSrv.duplicateFields(source, Fields.empty.set("name", "copy").set("description", "  ")).get
      fields.getString("description") must beSome("Send an email")
    }

    "tolerate a source without configuration or options" in {
      val bare   = Json.obj("name" -> "Echo_1_0", "description" -> "echo", "rate" -> JsNull)
      val fields = WorkerSrv.duplicateFields(bare, Fields.empty.set("name", "Echo copy").set("configuration", Json.obj("k" -> "v"))).get
      fields.getValue("configuration") must beSome(Json.obj("k" -> JsString("v")))
      fields.contains("rate") must beFalse
    }
  }
}
