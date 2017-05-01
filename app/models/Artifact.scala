package models

import play.api.libs.json.JsObject
import java.io.File

abstract class Artifact(attributes: JsObject) {
  def dataTypeFilter(filter: String): Boolean = (attributes \ "dataType").asOpt[String].fold(false)(_.toLowerCase.contains(filter.toLowerCase))
  def dataFilter(filter: String): Boolean = false
}
class FileArtifact(val data: File, val attributes: JsObject) extends Artifact(attributes) {
  override def finalize {
    data.delete()
  }
}
object FileArtifact {
  def apply(data: File, attributes: JsObject): FileArtifact = {
    val tempFile = File.createTempFile("cortex-", "-datafile")
    data.renameTo(tempFile)
    new FileArtifact(tempFile, attributes)
  }
  def unapply(fileArtifact: FileArtifact) = Some(fileArtifact.data → fileArtifact.attributes)
}
case class DataArtifact(data: String, attributes: JsObject) extends Artifact(attributes) {
  override def dataFilter(filter: String): Boolean = data.toLowerCase.contains(filter.toLowerCase)
}
