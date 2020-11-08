package org.thp.cortex.models

import javax.inject.{Inject, Singleton}

import play.api.libs.json.JsObject

import org.elastic4play.models.{AttributeDef, EntityDef, AttributeFormat => F, AttributeOption => O, ChildModelDef}

trait ArtifactAttributes { _: AttributeDef =>
  val dataType   = attribute("dataType", F.stringFmt, "Type of the artifact", O.readonly)
  val data       = optionalAttribute("data", F.rawFmt, "Content of the artifact", O.readonly)
  val attachment = optionalAttribute("attachment", F.attachmentFmt, "Artifact file content", O.readonly)
  val tlp        = attribute("tlp", TlpAttributeFormat, "TLP level", 2L)
  val tags       = multiAttribute("tags", F.stringFmt, "Artifact tags")
  val message    = optionalAttribute("message", F.textFmt, "Message associated to the analysis")
}

@Singleton
class ArtifactModel @Inject() (reportModel: ReportModel)
    extends ChildModelDef[ArtifactModel, Artifact, ReportModel, Report](reportModel, "artifact", "Artifact", "/artifact")
    with ArtifactAttributes {}

class Artifact(model: ArtifactModel, attributes: JsObject) extends EntityDef[ArtifactModel, Artifact](model, attributes) with ArtifactAttributes
