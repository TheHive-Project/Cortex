package org.thp.cortex.controllers

import javax.inject.{Inject, Singleton}
import play.api.http.{FileMimeTypes, HttpErrorHandler}
import play.api.mvc.{Action, AnyContent}
import controllers.{Assets, AssetsMetadata, ExternalAssets}
import play.api.Environment

import scala.concurrent.ExecutionContext

trait AssetCtrl {
  def get(file: String): Action[AnyContent]
}

@Singleton
class AssetCtrlProd @Inject() (errorHandler: HttpErrorHandler, meta: AssetsMetadata) extends Assets(errorHandler, meta) with AssetCtrl {
  def get(file: String): Action[AnyContent] = at("/www", file)
}

@Singleton
class AssetCtrlDev @Inject() (environment: Environment)(implicit ec: ExecutionContext, fileMimeTypes: FileMimeTypes)
    extends ExternalAssets(environment)
    with AssetCtrl {
  def get(file: String): Action[AnyContent] = at("www/dist", file)
}
