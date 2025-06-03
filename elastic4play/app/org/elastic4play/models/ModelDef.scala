package org.elastic4play.models

import java.util.Date

import scala.concurrent.Future
import scala.language.higherKinds

import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.{JsObject, JsString, Json}

import org.elastic4play.InternalError

trait AttributeDef {
  type A[B]

  def attribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => T],
      options: AttributeOption.Type*
  ): A[T]

  def attribute[T](attributeName: String, format: AttributeFormat[T], description: String, defaultValue: => T, options: AttributeOption.Type*): A[T] =
    attribute(attributeName, format, description, Some(() => defaultValue), options: _*)

  def attribute[T](attributeName: String, format: AttributeFormat[T], description: String, options: AttributeOption.Type*): A[T] =
    attribute(attributeName, format, description, None, options: _*)

  def multiAttribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => Seq[T]],
      options: AttributeOption.Type*
  ): A[Seq[T]]

  def multiAttribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Seq[T],
      options: AttributeOption.Type*
  ): A[Seq[T]] =
    multiAttribute(attributeName, format, description, Some(() => defaultValue), options: _*)

  def multiAttribute[T](attributeName: String, format: AttributeFormat[T], description: String, options: AttributeOption.Type*): A[Seq[T]] =
    multiAttribute(attributeName, format, description, None, options: _*)

  def optionalAttribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => Option[T]],
      options: AttributeOption.Type*
  ): A[Option[T]]

  def optionalAttribute[T](attributeName: String, format: AttributeFormat[T], description: String, options: AttributeOption.Type*): A[Option[T]] =
    optionalAttribute(attributeName, format, description, None: Option[() => Option[T]], options: _*)
}

abstract class ModelAttributes(val modelName: String) extends AttributeDef {
  type A[B] = Attribute[B]
  private var _attributes: Seq[Attribute[_]] = Nil
  def attributes: Seq[Attribute[_]]          = _attributes

  /* attribute creation helper */
  def attribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => T],
      options: AttributeOption.Type*
  ): Attribute[T] = {
    val attr = Attribute(modelName, attributeName, format, options, defaultValue, description: String)
    _attributes = attr +: _attributes
    attr
  }

  def multiAttribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => Seq[T]],
      options: AttributeOption.Type*
  ): Attribute[Seq[T]] = {
    val attr = Attribute(modelName, attributeName, MultiAttributeFormat(format), options, defaultValue, description: String)
    _attributes = attr +: _attributes
    attr
  }

  def optionalAttribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => Option[T]],
      options: AttributeOption.Type*
  ): Attribute[Option[T]] = {
    val attr = Attribute(modelName, attributeName, OptionalAttributeFormat(format), options, defaultValue, description: String)
    _attributes = attr +: _attributes
    attr
  }

  val createdBy: Attribute[String] =
    attribute("createdBy", AttributeFormat.userFmt, "user who created this entity", None, AttributeOption.model, AttributeOption.readonly)

  val createdAt: Attribute[Date] =
    attribute("createdAt", AttributeFormat.dateFmt, "user who created this entity", new Date, AttributeOption.model, AttributeOption.readonly)

  val updatedBy: Attribute[Option[String]] =
    optionalAttribute("updatedBy", AttributeFormat.userFmt, "user who created this entity", None, AttributeOption.model)

  val updatedAt: Attribute[Option[Date]] =
    optionalAttribute("updatedAt", AttributeFormat.dateFmt, "user who created this entity", AttributeOption.model)
}

abstract class BaseModelDef(modelName: String, val label: String, val path: String) extends ModelAttributes(modelName) {
  def apply(attributes: JsObject): BaseEntity
  def removeAttribute: JsObject = throw InternalError(s"$modelName can't be removed")

  /* default sort parameter used in List and Search controllers */
  def defaultSortBy: Seq[String] = Nil

  /* get attributes definitions for the entity (form, model, required and default values) */
  def formAttributes: Map[String, Attribute[_]] =
    attributes.collect { case a if a.isForm => a.attributeName -> a }.toMap

  /* get attributes definitions for the entity (form, model, required and default values) */
  def modelAttributes: Map[String, Attribute[_]] =
    attributes.collect { case a if a.isModel => a.attributeName -> a }.toMap

  lazy val attachmentAttributes: Map[String, Boolean] = formAttributes
    .filter(_._2.format match {
      case `AttachmentAttributeFormat`                                      => true
      case OptionalAttributeFormat(fmt) if fmt == AttachmentAttributeFormat => true
      case MultiAttributeFormat(fmt) if fmt == AttachmentAttributeFormat    => true
      case _                                                                => false
    })
    .view
    .mapValues(_.isRequired)
    .toMap

  /* this hook, executed on creation can be override by subclass in order to transform entity attributes */
  def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = Future.successful(attrs)

  /* this hook, executed on update can be override by subclass in order to transform entity attributes */
  def updateHook(entity: BaseEntity, updateAttrs: JsObject): Future[JsObject] = Future.successful(updateAttrs)

  def getStats(entity: BaseEntity): Future[JsObject] = Future.successful(JsObject.empty)

  val computedMetrics = Map.empty[String, String]
}

class BaseEntity(val model: BaseModelDef, val attributes: JsObject) {
  val id: String                    = (attributes \ "_id").as[String]
  val routing: String               = (attributes \ "_routing").as[String]
  lazy val parentId: Option[String] = (attributes \ "_parent").asOpt[String]
  val seqNo: Long                   = (attributes \ "_seqNo").as[Long]
  val primaryTerm: Long             = (attributes \ "_primaryTerm").as[Long]
  def createdBy: String             = (attributes \ "createdBy").as[String]
  def createdAt: Date               = (attributes \ "createdAt").as[Date]
  def updatedBy: String             = (attributes \ "updatedBy").as[String]
  def updatedAt: Date               = (attributes \ "updatedAt").as[Date]

  @inline
  final private def removeProtectedAttributes(attrs: JsObject) = JsObject {
    attrs
      .fields
      .map { case (name, value) => (name, value, model.attributes.find(_.attributeName == name)) }
      .collect {
        case (name, value, Some(desc)) if !desc.isSensitive => name -> value
        case (name, value, _) if name.startsWith("_")       => name -> value
      }
  }

  def toJson: JsObject =
    removeProtectedAttributes(attributes) +
      ("id" -> JsString(id))

  /* compute auxiliary data */
  override def toString: String = Json.prettyPrint(toJson)
}

abstract class EntityDef[M <: BaseModelDef, E <: BaseEntity](model: M, attributes: JsObject) extends BaseEntity(model, attributes) with AttributeDef {
  self: E =>
  type A[B] = () => B

  def attribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => T],
      options: AttributeOption.Type*
  ): A[T] = { () =>
    (attributes \ attributeName).asOpt[T](format.jsFormat).getOrElse(throw InvalidEntityAttributes[M, T](model, attributeName, format, attributes))
  }

  def multiAttribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => Seq[T]],
      options: AttributeOption.Type*
  ): A[Seq[T]] = { () =>
    (attributes \ attributeName).asOpt[Seq[T]](MultiAttributeFormat(format).jsFormat).getOrElse(Nil)
  }

  def optionalAttribute[T](
      attributeName: String,
      format: AttributeFormat[T],
      description: String,
      defaultValue: Option[() => Option[T]],
      options: AttributeOption.Type*
  ): A[Option[T]] = { () =>
    (attributes \ attributeName).asOpt[T](format.jsFormat)
  }
}

abstract class AbstractModelDef[M <: AbstractModelDef[M, E], E <: BaseEntity](modelName: String, label: String, path: String)
    extends BaseModelDef(modelName, label, path) {
  override def apply(attributes: JsObject): E
}

abstract class ModelDef[M <: ModelDef[M, E], E <: BaseEntity](modelName: String, label: String, path: String)(implicit e: Manifest[E])
    extends AbstractModelDef[M, E](modelName, label, path) { self: M =>
  override def apply(attributes: JsObject): E =
    e.runtimeClass.getConstructor(getClass, classOf[JsObject]).newInstance(self, attributes).asInstanceOf[E]
}
abstract class ChildModelDef[M <: ChildModelDef[M, E, PM, PE], E <: BaseEntity, PM <: BaseModelDef, PE <: BaseEntity](
    val parentModel: PM,
    modelName: String,
    label: String,
    path: String
)(implicit e: Manifest[E])
    extends AbstractModelDef[M, E](modelName, label, path) { self: M =>
  override def apply(attributes: JsObject): E =
    e.runtimeClass.getConstructor(getClass, classOf[JsObject]).newInstance(self, attributes).asInstanceOf[E]
}
