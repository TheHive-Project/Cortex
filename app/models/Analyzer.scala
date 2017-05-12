package models

abstract class Analyzer {
  val name: String
  val version: String
  val description: String
  val dataTypeList: Seq[String]
  val author: String
  val url: String
  val license: String
  val id: String = (name + "_" + version).replaceAll("\\.", "_")
}
