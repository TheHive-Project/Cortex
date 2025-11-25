package org.elastic4play.services

import javax.inject.{Inject, Provider, Singleton}

import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}

import org.elastic4play.database.DBCreate
import org.elastic4play.models.{Attribute, EntityDef, ModelDef, AttributeFormat => F}
import org.elastic4play.utils.{Hasher, RichFuture}

@Singleton
class DBListModel(dblistName: String) extends ModelDef[DBListModel, DBListItemEntity](dblistName, "DBList", "/list") {
  model =>
  @Inject def this(configuration: Configuration) = this(configuration.get[String]("dblist.name"))

  val value: Attribute[String]  = attribute("value", F.stringFmt, "Content of the dblist item")
  val dblist: Attribute[String] = attribute("dblist", F.stringFmt, "Name of the dblist")

  override def apply(attributes: JsObject) = new DBListItemEntity(this, attributes)

}

class DBListItemEntity(model: DBListModel, attributes: JsObject) extends EntityDef[DBListModel, DBListItemEntity](model, attributes) with DBListItem {
  def mapTo[T](implicit reads: Reads[T]): T = Json.parse((attributes \ "value").as[String]).as[T]

  def dblist: String = (attributes \ "dblist").as[String]

  override def toJson: JsObject = super.toJson - "value" + ("value" -> mapTo[JsValue])
}

trait DBListItem {
  def id: String

  def dblist: String

  def mapTo[A](implicit reads: Reads[A]): A
}

trait DBList {
  def cachedItems: Seq[DBListItem]

  def getItems(): (Source[DBListItem, NotUsed], Future[Long])

  def getItems[A: Reads]: (Source[(String, A), NotUsed], Future[Long])

  def addItem[A: Writes](item: A): Future[DBListItem]

  def exists(key: String, value: JsValue): Future[Boolean]
}

@Singleton
class DBLists @Inject() (
    getSrv: GetSrv,
    findSrv: FindSrv,
    deleteSrv: Provider[DeleteSrv],
    dbCreate: DBCreate,
    dblistModel: DBListModel,
    cache: AsyncCacheApi,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  /**
    * Returns list of all dblist name
    */
  def listAll: Future[collection.Set[String]] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(dblistModel, any, groupByField("dblist", selectCount)).map(_.keys)
  }

  def deleteItem(itemId: String)(implicit authContext: AuthContext): Future[Unit] =
    getItem(itemId).flatMap(deleteItem)

  def deleteItem(item: DBListItemEntity)(implicit authContext: AuthContext): Future[Unit] =
    for {
      _ <- deleteSrv.get.realDelete(item)
      _ = cache.remove(dblistModel.modelName + "_" + item.dblist)
    } yield ()

  def getItem(itemId: String): Future[DBListItemEntity] = getSrv[DBListModel, DBListItemEntity](dblistModel, itemId)

  def apply(name: String): DBList = new DBList {

    def cachedItems: immutable.Seq[DBListItem] =
      cache
        .getOrElseUpdate(dblistModel.modelName + "_" + name, 10.seconds) {
          val (src, _) = getItems()
          src.runWith(Sink.seq)
        }
        .await

    def getItems(): (Source[DBListItem, NotUsed], Future[Long]) = {
      import org.elastic4play.services.QueryDSL._
      findSrv[DBListModel, DBListItemEntity](dblistModel, "dblist" ~= name, Some("all"), Nil)
    }

    override def getItems[A: Reads]: (Source[(String, A), NotUsed], Future[Long]) = {
      val (src, total) = getItems()
      val items        = src.map(item => (item.id, item.mapTo[A]))
      (items, total)
    }

    override def addItem[A: Writes](item: A): Future[DBListItem] = {
      val value = Json.toJson(item)
      val id    = Hasher("MD5").fromString(value.toString).head.toString
      dbCreate(dblistModel.modelName, None, Json.obj("_id" -> id, "dblist" -> name, "value" -> JsString(value.toString)))
        .map { newItem =>
          cache.remove(dblistModel.modelName + "_" + name)
          dblistModel(newItem)
        }
    }

    def exists(key: String, value: JsValue): Future[Boolean] =
      getItems()
        ._1
        .filter { item =>
          item
            .mapTo[JsValue]
            .asOpt[JsObject]
            .flatMap { obj =>
              (obj \ key).asOpt[JsValue]
            }
            .contains(value)
        }
        .runWith(Sink.headOption)
        .map(_.isDefined)
  }

}
