package models

import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

object JsonFormat {
  implicit val analyzerWrites: OWrites[Analyzer] = OWrites[Analyzer] { analyzer ⇒
    Json.obj(
      "name" → analyzer.name,
      "version" → analyzer.version,
      "description" → analyzer.description,
      "dataTypeList" → analyzer.dataTypeList,
      "author" → analyzer.author,
      "url" → analyzer.url,
      "license" → analyzer.license,
      "id" → analyzer.id)
  }

  implicit val fileArtifactWrites: OWrites[FileArtifact] = OWrites[FileArtifact] { fileArtifact ⇒
    Json.obj("attributes" → fileArtifact.attributes)
  }

  implicit val dataArtifactWrites: OWrites[DataArtifact] = Json.writes[DataArtifact]
  implicit val dataActifactReads: Reads[DataArtifact] = Json.reads[DataArtifact]

  implicit val artifactWrites: OWrites[Artifact] = OWrites[Artifact] {
    case dataArtifact: DataArtifact ⇒ dataArtifactWrites.writes(dataArtifact)
    case fileArtifact: FileArtifact ⇒ fileArtifactWrites.writes(fileArtifact)
  }

  implicit val jobStatusWrites: Writes[JobStatus.Type] = Writes[JobStatus.Type](jobStatus ⇒ JsString(jobStatus.toString))

  implicit val jobWrites: OWrites[Job] = OWrites[Job](job ⇒ Json.obj(
    "id" → job.id,
    "analyzerId" → job.analyzerId,
    "status" → job.status,
    "date" → job.date,
    "artifact" → job.artifact))

}
