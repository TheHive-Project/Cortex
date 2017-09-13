package models

import play.api.libs.json.{ JsObject, JsValue }

sealed abstract class Report(success: Boolean)

case class SuccessReport(artifacts: Seq[Artifact], full: JsObject, summary: JsObject) extends Report(true)

case class FailureReport(message: String, input: JsValue) extends Report(false)