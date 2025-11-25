package org.elastic4play.services

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import javax.inject.{Inject, Singleton}
import org.elastic4play.InternalError
import org.elastic4play.models.{AttributeOption, BaseEntity, ChildModelDef}
import play.api.Logger
import play.api.libs.json.JsObject

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuxSrv @Inject() (findSrv: FindSrv, modelSrv: ModelSrv, implicit val mat: Materializer) {

  import org.elastic4play.services.QueryDSL._

  private[AuxSrv] lazy val logger = Logger(getClass)

  def filterAttributes(entity: BaseEntity, filter: Seq[AttributeOption.Type] => Boolean): JsObject =
    entity.model.attributes.foldLeft(entity.toJson) {
      case (json, attribute) if !filter(attribute.options) => json - attribute.attributeName
      case (json, _)                                       => json
    }

  def apply(entity: BaseEntity, nparent: Int, withStats: Boolean, removeUnaudited: Boolean)(implicit ec: ExecutionContext): Future[JsObject] =
    apply(entity, nparent, withStats, opts => !removeUnaudited || !opts.contains(AttributeOption.unaudited))

  def apply(entity: BaseEntity, nparent: Int, withStats: Boolean, filter: Seq[AttributeOption.Type] => Boolean)(
      implicit ec: ExecutionContext
  ): Future[JsObject] = {
    val entityWithParent = entity.model match {
      case childModel: ChildModelDef[_, _, _, _] if nparent > 0 =>
        val (src, _) = findSrv(
          childModel.parentModel,
          "_id" ~= entity.parentId.getOrElse(throw InternalError(s"Child entity $entity has no parent ID")),
          Some("0-1"),
          Nil
        )
        src
          .mapAsync(1) { parent =>
            apply(parent, nparent - 1, withStats, filter).map { parent =>
              val entityObj = filterAttributes(entity, filter)
              entityObj + (childModel.parentModel.modelName -> parent)
            }
          }
          .runWith(Sink.headOption)
          .map(_.getOrElse {
            logger.warn(s"Child entity (${childModel.modelName} ${entity.id}) has no parent !")
            JsObject.empty
          })
      case _ => Future.successful(filterAttributes(entity, filter))
    }
    if (withStats) {
      for {
        e <- entityWithParent
        s <- entity.model.getStats(entity)
      } yield e + ("stats" -> s)
    } else entityWithParent
  }

  def apply[A](entities: Source[BaseEntity, A], nparent: Int, withStats: Boolean, removeUnaudited: Boolean)(
      implicit ec: ExecutionContext
  ): Source[JsObject, A] =
    entities.mapAsync(5) { entity =>
      apply(entity, nparent, withStats, removeUnaudited)
    }

  def apply(modelName: String, entityId: String, nparent: Int, withStats: Boolean, removeUnaudited: Boolean)(
      implicit ec: ExecutionContext
  ): Future[JsObject] = {
    if (entityId == "")
      return Future.successful(JsObject.empty)
    modelSrv(modelName)
      .map { model =>
        val (src, _) = findSrv(model, "_id" ~= entityId, Some("0-1"), Nil)
        src
          .mapAsync(1) { entity =>
            apply(entity, nparent, withStats, removeUnaudited)
          }
          .runWith(Sink.headOption)
          .map(_.getOrElse {
            logger.warn(s"Entity $modelName $entityId not found")
            JsObject.empty
          })
      }
      .getOrElse(Future.failed(InternalError(s"Model $modelName not found")))
  }
}
