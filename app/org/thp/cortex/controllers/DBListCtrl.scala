package org.thp.cortex.controllers

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

import org.thp.cortex.models.Roles

import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.services.DBLists
import org.elastic4play.MissingAttributeError

@Singleton
class DBListCtrl @Inject() (
    dblists: DBLists,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  def list: Action[AnyContent] = authenticated(Roles.read).async { _ =>
    dblists.listAll.map { listNames =>
      renderer.toOutput(OK, listNames)
    }
  }

  def listItems(listName: String): Action[AnyContent] = authenticated(Roles.read) { _ =>
    val (src, _) = dblists(listName).getItems[JsValue]
    val items = src
      .map { case (id, value) => s""""$id":$value""" }
      .intersperse("{", ",", "}")
    Ok.chunked(items).as("application/json")
  }

  def addItem(listName: String): Action[Fields] = authenticated(Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    request.body.getValue("value").fold(Future.successful(NoContent)) { value =>
      dblists(listName).addItem(value).map { item =>
        renderer.toOutput(OK, item.id)
      }
    }
  }

  def deleteItem(itemId: String): Action[AnyContent] = authenticated(Roles.superAdmin).async { implicit request =>
    dblists.deleteItem(itemId).map { _ =>
      NoContent
    }
  }

  def updateItem(itemId: String): Action[Fields] = authenticated(Roles.superAdmin).async(fieldsBodyParser) { implicit request =>
    request
      .body
      .getValue("value")
      .map { value =>
        for {
          item    <- dblists.getItem(itemId)
          _       <- dblists.deleteItem(item)
          newItem <- dblists(item.dblist).addItem(value)
        } yield renderer.toOutput(OK, newItem.id)
      }
      .getOrElse(Future.failed(MissingAttributeError("value")))
  }

  def itemExists(listName: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val itemKey   = request.body.getString("key").getOrElse(throw MissingAttributeError("Parameter key is missing"))
    val itemValue = request.body.getValue("value").getOrElse(throw MissingAttributeError("Parameter value is missing"))
    dblists(listName).exists(itemKey, itemValue).map(r => Ok(Json.obj("found" -> r)))
  }
}
