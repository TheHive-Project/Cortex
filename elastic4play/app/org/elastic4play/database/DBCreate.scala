package org.elastic4play.database

import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.streams.RequestBuilder
import javax.inject.{Inject, Singleton}
import org.elastic4play.models.BaseEntity

/**
  * Service lass responsible for entity creation
  * This service doesn't check any attribute conformity (according to model)
  */
@Singleton
class DBCreate @Inject() (db: DBConfiguration) {

  private[DBCreate] lazy val logger = Logger(getClass)

  /**
    * Create an entity of type "modelName" with attributes
    *
    * @param modelName name of the model of the creating entity
    * @param attributes JSON object containing attributes of the creating entity. Attributes can contain _id, _parent and _routing.
    * @return created entity attributes with _id and _routing (and _parent if entity is a child)
    */
  def apply(modelName: String, attributes: JsObject)(implicit ec: ExecutionContext): Future[JsObject] =
    apply(modelName, None, attributes)

  /**
    * Create an entity of type modelName with attributes and optionally a parent
    *
    * @param modelName name of the model of the creating entity
    * @param parent parent of the creating entity (if model is ChildModelDef
    * @param attributes JSON object containing attributes of the creating entity.
    * Attributes can contain _id, _parent and _routing. Routing and parent informations are extracted from parent parameter (if present)
    * @return created entity attributes with _id and _routing (and _parent if entity is a child)
    */
  def apply(modelName: String, parent: Option[BaseEntity], attributes: JsObject)(implicit ec: ExecutionContext): Future[JsObject] = {
    val id = (attributes \ "_id").asOpt[String]
    val parentId = parent
      .map(_.id)
      .orElse((attributes \ "_parent").asOpt[String])
    val routing = parent
      .map(_.routing)
      .orElse((attributes \ "_routing").asOpt[String])
      .orElse(id)

    // remove attributes that starts with "_" because we wan't permit to interfere with elasticsearch internal fields
    val docSource = addParent(modelName, parent, JsObject(attributes.fields.filterNot(_._1.startsWith("_"))))

    db.execute {
        addId(id).andThen(addRouting(routing)) {
          indexInto(db.indexName).source(docSource.toString).refresh(RefreshPolicy.WAIT_FOR)
        }
      }
      .map(indexResponse =>
        attributes +
          ("_type"        -> JsString(modelName)) +
          ("_id"          -> JsString(indexResponse.id)) +
          ("_parent"      -> parentId.fold[JsValue](JsNull)(JsString)) +
          ("_routing"     -> JsString(routing.getOrElse(indexResponse.id))) +
          ("_seqNo"       -> JsNumber(indexResponse.seqNo)) +
          ("_primaryTerm" -> JsNumber(indexResponse.primaryTerm))
      )
  }

  /**
    * add id information in index definition
    */
  private def addId(id: Option[String]): IndexRequest => IndexRequest = id match {
    case Some(i) => _ id i createOnly true
    case None    => identity
  }

  /**
    * add routing information in index definition
    */
  private def addRouting(routing: Option[String]): IndexRequest => IndexRequest = routing match {
    case Some(r) => _ routing r
    case None    => identity
  }

  private def addParent(modelName: String, parent: Option[BaseEntity], entity: JsObject): JsObject = parent match {
    case Some(p) => entity + ("relations" -> Json.obj("name" -> modelName, "parent" -> p.id))
    case None    => entity + ("relations" -> JsString(modelName))
  }

  /**
    * Class used to build index definition based on model name and attributes
    * This class is used by sink (ElasticSearch reactive stream)
    */
  private class AttributeRequestBuilder() extends RequestBuilder[JsObject] {
    override def request(attributes: JsObject): IndexRequest = {
      val id        = (attributes \ "_id").asOpt[String]
      val routing   = (attributes \ "_routing").asOpt[String] orElse id
      val docSource = JsObject(attributes.fields.filterNot(_._1.startsWith("_")))
      addId(id).andThen(addRouting(routing)) {
        indexInto(db.indexName).source(docSource.toString)
      }
    }
  }

  /**
    * build a akka stream sink that create entities
    */
  def sink()(implicit ec: ExecutionContext): Sink[JsObject, Future[Unit]] = db.sink(new AttributeRequestBuilder(), ec)
}
