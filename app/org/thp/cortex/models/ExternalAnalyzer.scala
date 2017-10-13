//package models
//
//import java.nio.file.Path
//
//import play.api.libs.json.JsObject
//
//case class ExternalAnalyzer(
//    name: String,
//    version: String,
//    description: String,
//    dataTypeList: Seq[String],
//    author: String,
//    url: String,
//    license: String,
//    command: Path,
//    config: JsObject) extends Analyzer