package models

import java.io.File
import java.nio.file.Files

import play.api.libs.json.JsObject

abstract class Artifact(attributes: JsObject) {
  def dataType: String = (attributes \ "dataType").asOpt[String].getOrElse("other")
  def dataTypeFilter(filter: String): Boolean = dataType.toLowerCase.contains(filter.toLowerCase)
  def dataFilter(filter: String): Boolean = false
}
class FileArtifact(val data: File, val attributes: JsObject) extends Artifact(attributes) {
  override def finalize() {
    data.delete()
  }
}
object FileArtifact {
  def apply(data: File, attributes: JsObject): FileArtifact = {
    val tempFile = File.createTempFile("cortex-", "-datafile")
    data.renameTo(tempFile)
    new FileArtifact(tempFile, attributes)
  }
  def apply(data: Array[Byte], attributes: JsObject): FileArtifact = {
    val tempFile = File.createTempFile("cortex-", "-datafile")
    Files.write(tempFile.toPath, data)
    new FileArtifact(tempFile, attributes)
  }
  def unapply(fileArtifact: FileArtifact) = Some(fileArtifact.data → fileArtifact.attributes)
}
case class DataArtifact(data: String, attributes: JsObject) extends Artifact(attributes) {
  override def dataFilter(filter: String): Boolean = data.toLowerCase.contains(filter.toLowerCase)
}
