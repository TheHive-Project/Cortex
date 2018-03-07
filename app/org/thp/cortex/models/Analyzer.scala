package org.thp.cortex.models

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future
import scala.util.Try

import play.api.libs.json.{ JsObject, JsString, Json }

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.models.{ AttributeDef, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat ⇒ F, AttributeOption ⇒ O }
import org.elastic4play.utils.Hasher

object RateUnit extends Enumeration with HiveEnumeration {
  type Type = Value
  val Day = Value(24 * 60 * 60)
  val Month = Value(30 * 24 * 60 * 60)
  implicit val reads = enumFormat(this)
}

trait AnalyzerAttributes { _: AttributeDef ⇒
  val analyzerId = attribute("_id", F.stringFmt, "Analyzer id", O.model)
  val name = attribute("name", F.stringFmt, "Analyzer name")
  val analyzerDefinitionId = attribute("analyzerDefinitionId", F.stringFmt, "Analyzer definition id", O.readonly)
  val description = attribute("description", F.textFmt, "Analyzer description")
  val dataTypeList = multiAttribute("dataTypeList", F.stringFmt, "List of data type this analyzer can manage")
  val configuration = attribute("configuration", F.textFmt, "Configuration of analyzer", O.sensitive)
  val rate = optionalAttribute("rate", F.numberFmt, "Number ")
  val rateUnit = optionalAttribute("rateUnit", F.enumFmt(RateUnit), "")
}

@Singleton
class AnalyzerModel @Inject() (organizationModel: OrganizationModel) extends ChildModelDef[AnalyzerModel, Analyzer, OrganizationModel, Organization](organizationModel, "analyzer", "Analyzer", "/analyzer") with AnalyzerAttributes with AuditedModel {
  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    val hasher = Hasher("md5")
    val id = for {
      organizationId ← parent.map(_.id)
      name ← (attrs \ "name").asOpt[String]
    } yield hasher.fromString(s"${organizationId}_$name").head.toString
    Future.successful(attrs + ("_id" -> JsString(id.getOrElse("<null>"))))
  }
}

class Analyzer(model: AnalyzerModel, attributes: JsObject) extends EntityDef[AnalyzerModel, Analyzer](model, attributes) with AnalyzerAttributes {
  def config: JsObject = Try(Json.parse(configuration()).as[JsObject]).getOrElse(JsObject.empty)
  def organization = parentId.get
}
