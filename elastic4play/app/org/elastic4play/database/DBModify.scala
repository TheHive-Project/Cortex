package org.elastic4play.database

import java.util.{Map => JMap}

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.JacksonSupport
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.script.Script
import javax.inject.{Inject, Singleton}
import org.elastic4play.models.BaseEntity
import play.api.Logger
import play.api.libs.json._
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}
import java.util.{Map => JMap}

case class ModifyConfig(
    retryOnConflict: Int = 5,
    refreshPolicy: RefreshPolicy = RefreshPolicy.WAIT_FOR,
    seqNoAndPrimaryTerm: Option[(Long, Long)] = None
)

object ModifyConfig {
  def default: ModifyConfig = ModifyConfig(5, RefreshPolicy.WAIT_FOR, None)
}

@Singleton
class DBModify @Inject() (db: DBConfiguration) {
  private[DBModify] lazy val logger = Logger(getClass)

  /**
    * Convert JSON value to java native value
    */
  private[database] def jsonToAny(json: JsValue): Any = {
    json match {
      case v: JsObject  => v.fields.map { case (k, v) => k -> jsonToAny(v) }.toMap.asJava
      case v: JsArray   => v.value.map(jsonToAny).toArray
      case v: JsNumber  => v.value.toLong
      case v: JsString  => v.value
      case v: JsBoolean => v.value
      case JsNull       => null
    }
  }

  /**
    * Build the parameters needed to update ElasticSearch document
    * Parameters contains update script, parameters for the script
    * As null is a valid value to set, in order to remove an attribute an empty array must be used.
    *
    * @param entity entity to update
    * @param updateAttributes contains attributes to update. JSON object contains key (attribute name) and value.
    *   Sub attribute can be updated using dot notation ("attr.subattribute").
    * @return ElasticSearch update script
    */
  private[database] def buildScript(entity: BaseEntity, updateAttributes: JsObject): Script = {
    val attrs = updateAttributes.fields.zipWithIndex
    val updateScript = attrs.map {
      case ((name, JsArray(Seq())), _) =>
        val names = name.split("\\.")
        names.init.map(n => s"""["$n"]""").mkString("ctx._source", "", s""".remove("${names.last}")""")
      case ((name, JsNull), _) =>
        name.split("\\.").map(n => s"""["$n"]""").mkString("ctx._source", "", s"=null")
      case ((name, _), index) =>
        name.split("\\.").map(n => s"""["$n"]""").mkString("ctx._source", "", s"=params.param$index")
    } mkString ";"

    val parameters = jsonToAny(JsObject(attrs.collect {
      case ((_, value), index) if value != JsArray(Nil) && value != JsNull => s"param$index" -> value
    })).asInstanceOf[JMap[String, Any]].asScala.toMap

    Script(updateScript).params(parameters)
  }

  /**
    * Update entity with new attributes contained in JSON object
    *
    * @param entity entity to update
    * @param updateAttributes contains attributes to update. JSON object contains key (attribute name) and value.
    *   Sub attribute can be updated using dot notation ("attr.subattribute").
    * @param modifyConfig modification parameter (retryOnConflict and refresh policy)
    * @return new version of the entity
    */
  def apply(entity: BaseEntity, updateAttributes: JsObject, modifyConfig: ModifyConfig)(implicit ec: ExecutionContext): Future[BaseEntity] =
    db.execute {
        val updateDefinition = updateById(db.indexName, entity.id)
          .routing(entity.routing)
          .script(buildScript(entity, updateAttributes))
          .fetchSource(true)
          .retryOnConflict(modifyConfig.retryOnConflict)
          .refresh(modifyConfig.refreshPolicy)
        modifyConfig.seqNoAndPrimaryTerm.fold(updateDefinition)(s => updateDefinition.ifSeqNo(s._1).ifPrimaryTerm(s._2))
      }
      .map { updateResponse =>
        entity.model(
          Json.parse(JacksonSupport.mapper.writeValueAsString(updateResponse.source)).as[JsObject] +
            ("_type"        -> JsString(entity.model.modelName)) +
            ("_id"          -> JsString(entity.id)) +
            ("_routing"     -> JsString(entity.routing)) +
            ("_parent"      -> entity.parentId.fold[JsValue](JsNull)(JsString)) +
            ("_seqNo"       -> JsNumber(updateResponse.seqNo)) +
            ("_primaryTerm" -> JsNumber(updateResponse.primaryTerm))
        )
      }
}
