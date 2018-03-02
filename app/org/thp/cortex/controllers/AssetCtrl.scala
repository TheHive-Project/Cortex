package org.thp.cortex.controllers

import javax.inject.{ Inject, Singleton }

import play.api.http.HttpErrorHandler
import play.api.mvc.{ Action, AnyContent }

import controllers.{ Assets, AssetsMetadata }

@Singleton
class AssetCtrl @Inject() (errorHandler: HttpErrorHandler, meta: AssetsMetadata) extends Assets(errorHandler, meta) {
  def get(file: String): Action[AnyContent] = at("/www", file)
}