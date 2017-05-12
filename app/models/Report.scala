package models

import play.api.libs.json.JsObject

sealed abstract class Report(success: Boolean)

case class SuccessReport(artifacts: Seq[Artifact], full: JsObject, summary: JsObject) extends Report(true)

case class FailureReport(message: String) extends Report(false)