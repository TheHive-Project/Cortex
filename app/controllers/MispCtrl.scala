package controllers

import javax.inject.Inject

import models.InvalidRequestError
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue }
import play.api.mvc.{ Action, AnyContent, Controller }
import services.MispSrv

import scala.concurrent.ExecutionContext

class MispCtrl @Inject() (mispSrv: MispSrv, implicit val ec: ExecutionContext) extends Controller {

  private[MispCtrl] lazy val logger = Logger(getClass)
  def modules: Action[AnyContent] = Action { _ ⇒
    Ok(mispSrv.moduleList)
  }

  def query: Action[JsValue] = Action.async(parse.json) { request ⇒
    val module = (request.body \ "module").asOpt[String].getOrElse(throw InvalidRequestError("Module parameter is not present in request"))
    val (mispType, dataJson) = request.body.as[JsObject].fields
      .collectFirst {
        case kv @ (k, _) if k != "module" ⇒ kv
      }
      .getOrElse(throw InvalidRequestError("Request doesn't contain data to analyze"))
    val data = dataJson.asOpt[String].getOrElse(throw InvalidRequestError("Data has invalid type (expected string)"))
    mispSrv.query(module, mispType, data)
      .map(Ok(_))
  }
}

