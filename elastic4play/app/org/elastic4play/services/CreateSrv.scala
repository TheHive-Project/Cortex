package org.elastic4play.services

import java.util.Date

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation.convertGenTraversableOnceToValidatable
import org.scalactic.Every.everyToGenTraversableOnce
import org.scalactic.{Bad, One}

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.controllers.Fields
import org.elastic4play.database.DBCreate
import org.elastic4play.models._
import org.elastic4play.utils.{RichFuture, RichOr}
import org.elastic4play.{AttributeCheckingError, UnknownAttributeError}

@Singleton
class CreateSrv @Inject() (
    fieldsSrv: FieldsSrv,
    dbCreate: DBCreate,
    eventSrv: EventSrv,
    attachmentSrv: AttachmentSrv
) {

  /**
    * Check if entity attributes are valid. Format is not checked as it has been already checked.
    */
  private[services] def checkAttributes(attrs: JsObject, model: BaseModelDef) =
    (attrs.keys ++ model.modelAttributes.keySet)
      .map { name =>
        (name, (attrs \ name).asOpt[JsValue], model.modelAttributes.get(name))
      }
      .validatedBy {
        case (name, value, Some(attr)) => attr.validateForCreation(value).map(name -> _)
        case (name, maybeValue, _)     => Bad(One(UnknownAttributeError(name, maybeValue.getOrElse(JsNull))))
      }
      .map(_.collect {
        case (name, Some(value)) => name -> value
      })
      .fold(attrs => Future.successful(JsObject(attrs.toSeq)), errors => Future.failed(AttributeCheckingError(model.modelName, errors)))

  private[services] def processAttributes(model: BaseModelDef, parent: Option[BaseEntity], attributes: JsObject)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[JsObject] =
    for {
      attributesAfterHook      <- model.creationHook(parent, addMetaFields(attributes))
      checkedAttributes        <- checkAttributes(attributesAfterHook, model)
      attributesWithAttachment <- attachmentSrv(model)(checkedAttributes)
    } yield attributesWithAttachment

  private[services] def addMetaFields(attrs: JsObject)(implicit authContext: AuthContext): JsObject =
    attrs ++
      Json.obj("createdBy" -> authContext.userId, "createdAt" -> Json.toJson(new Date))

  private[services] def removeMetaFields(attrs: JsObject): JsObject = attrs - "createdBy" - "createdAt"

  def apply[M <: ModelDef[M, E], E <: EntityDef[M, E]](model: M, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[E] =
    for {
      entityAttr <- create(model, None, fields) //dbCreate(model.name, None, attributesWithAttachment)
      entity = model(entityAttr)
      _      = eventSrv.publish(AuditOperation(entity, AuditableAction.Creation, removeMetaFields(entityAttr), authContext))
    } yield entity

  def apply[M <: ModelDef[M, E], E <: EntityDef[M, E]](
      model: M,
      fieldSet: Seq[Fields]
  )(implicit authContext: AuthContext, ec: ExecutionContext): Future[Seq[Try[E]]] =
    Future.sequence(fieldSet.map { fields =>
      create(model, None, fields).map { attr =>
        val entity = model(attr)
        eventSrv.publish(AuditOperation(entity, AuditableAction.Creation, removeMetaFields(attr), authContext))
        entity
      }.toTry
    })

  def apply[M <: ChildModelDef[M, E, _, PE], E <: EntityDef[M, E], PE <: BaseEntity](model: M, parent: PE, fields: Fields)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[E] =
    for {
      entityAttr <- create(model, Some(parent), fields)
      entity = model(entityAttr)
      _      = eventSrv.publish(AuditOperation(entity, AuditableAction.Creation, removeMetaFields(entityAttr), authContext))
    } yield entity

  def apply[M <: ChildModelDef[M, E, _, PE], E <: EntityDef[M, E], PE <: BaseEntity](model: M, fieldSet: Seq[(PE, Fields)])(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[Seq[Try[E]]] =
    Future.sequence(fieldSet.map {
      case (parent, fields) =>
        create(model, Some(parent), fields).map { attr =>
          val entity = model(attr)
          eventSrv.publish(AuditOperation(entity, AuditableAction.Creation, removeMetaFields(attr), authContext))
          entity
        }.toTry

    })

  private[services] def create(model: BaseModelDef, parent: Option[BaseEntity], fields: Fields)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[JsObject] =
    for {
      attrs                    <- fieldsSrv.parse(fields, model).toFuture
      attributesWithAttachment <- processAttributes(model, parent, attrs)
      entityAttr               <- dbCreate(model.modelName, parent, attributesWithAttachment)
    } yield entityAttr
}
