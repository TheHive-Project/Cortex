package models

case class MispModule(
    name: String,
    version: String,
    description: String,
    author: String,
    dataTypeList: Seq[String],
    inputAttributes: Seq[String],
    config: Seq[String],
    loaderCommand: String) extends Analyzer {
  val license = "AGPL-3.0"
  val url = "https://github.com/MISP/misp-modules"
}