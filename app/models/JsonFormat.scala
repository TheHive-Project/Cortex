package models

import scala.annotation.implicitNotFound

import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Writes
import scala.concurrent.Future
import play.api.libs.json.JsObject
import scala.util.Success
import scala.util.Failure
import play.api.libs.json.JsString
import play.api.libs.json.OWrites

object JsonFormat {
  implicit val analyzerWrites = Writes[Analyzer](analyzer ⇒ Json.obj(
    "name" → analyzer.name,
    "version" → analyzer.version,
    "description" → analyzer.description,
    "dataTypeList" → analyzer.dataTypeList,
    "author" → analyzer.author,
    "url" → analyzer.url,
    "license" → analyzer.license,
    "id" → analyzer.id))

  implicit val fileArtifactWrites = OWrites[FileArtifact](fileArtifact ⇒ Json.obj(
    "attributes" → fileArtifact.attributes))

  implicit val dataArtifactWrites = Json.writes[DataArtifact]
  implicit val dataActifactReads = Json.reads[DataArtifact]

  implicit val artifactWrites = OWrites[Artifact](artifact ⇒ artifact match {
    case dataArtifact: DataArtifact ⇒ dataArtifactWrites.writes(dataArtifact)
    case fileArtifact: FileArtifact ⇒ fileArtifactWrites.writes(fileArtifact)
  })

  implicit val jobStatusWrites = Writes[JobStatus.Type](jobStatus ⇒ JsString(jobStatus.toString))

  implicit val jobWrites = OWrites[Job](job ⇒ Json.obj(
    "id" → job.id,
    "analyzerId" → job.analyzerId,
    "status" → job.status,
    "date" → job.date,
    "artifact" → job.artifact))

}
