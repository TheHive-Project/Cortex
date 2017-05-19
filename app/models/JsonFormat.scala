package models

import models.JobStatus.Type
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{ JsString, Json, OWrites, Writes, _ }

object JsonFormat {
  implicit val analyzerWrites: Writes[Analyzer] = Writes[Analyzer](analyzer ⇒ Json.obj(
    "name" → analyzer.name,
    "version" → analyzer.version,
    "description" → analyzer.description,
    "dataTypeList" → analyzer.dataTypeList,
    "author" → analyzer.author,
    "url" → analyzer.url,
    "license" → analyzer.license,
    "id" → analyzer.id))

  implicit val fileArtifactWrites: OWrites[FileArtifact] = OWrites[FileArtifact](fileArtifact ⇒ Json.obj(
    "attributes" → fileArtifact.attributes))

  implicit val dataArtifactWrites: OWrites[DataArtifact] = Json.writes[DataArtifact]
  implicit val dataActifactReads: Reads[DataArtifact] = Json.reads[DataArtifact]

  val artifactWrites: OWrites[Artifact] = OWrites[Artifact] {
    case dataArtifact: DataArtifact ⇒ dataArtifactWrites.writes(dataArtifact)
    case fileArtifact: FileArtifact ⇒ fileArtifactWrites.writes(fileArtifact)
  }

  val artifactReads: Reads[Artifact] = Reads[Artifact] { json ⇒
    (json \ "data").asOpt[String]
      .map { data ⇒ JsSuccess(DataArtifact(data, json.as[JsObject])) }
      .getOrElse(JsError(__ \ "data", "data is missing"))
  }
  implicit val artifactFormat = Format(artifactReads, artifactWrites)

  implicit val jobStatusWrites: Writes[Type] = Writes[JobStatus.Type](jobStatus ⇒ JsString(jobStatus.toString))

  val reportArtifactReads: Reads[Artifact] =
    for {
      tpe ← (__ \ "type").read[String]
      value ← (__ \ "value").read[String]
    } yield DataArtifact(value, Json.obj("dataType" → tpe))

  val reportReads: Reads[Report] = Reads[Report] { json ⇒
    val success = (json \ "success").asOpt[Boolean].getOrElse(false)
    JsSuccess {
      if (success)
        (for {
          artifacts ← (json \ "artifacts").asOpt[Seq[Artifact]](Reads.seq(reportArtifactReads))
          full ← (json \ "full").asOpt[JsObject]
          summary ← (json \ "summary").asOpt[JsObject]
        } yield SuccessReport(artifacts, full, summary))
          .getOrElse(FailureReport(s"Invalid analyzer output format : $json"))
      else
        FailureReport((json \ "error").asOpt[String].getOrElse(json.toString))
    }
  }

  val reportWrites: Writes[Report] = Writes[Report] {
    case SuccessReport(artifacts, full, summary) ⇒ Json.obj(
      "artifacts" → artifacts,
      "full" → full,
      "summary" → summary,
      "success" → true)
    case FailureReport(message) ⇒ Json.obj(
      "errorMessage" → message,
      "success" → false)
  }

  implicit val reportFormat: Format[Report] = Format[Report](reportReads, reportWrites)

  implicit val jobWrites: OWrites[Job] = OWrites[Job] { job ⇒
    val report = job.report.value.flatMap(_.toOption).map(Json.toJson(_)).getOrElse(JsNull)
    Json.obj(
      "id" → job.id,
      "analyzerId" → job.analyzer.id,
      "status" → job.status,
      "date" → job.date,
      "artifact" → job.artifact,
      "report" → report)
  }
}
