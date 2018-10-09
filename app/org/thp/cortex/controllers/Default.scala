package org.thp.cortex.controllers

import play.api.Configuration
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import javax.inject.{ Inject, Singleton }

@Singleton
class Default @Inject() (configuration: Configuration, components: ControllerComponents) extends AbstractController(components) {
  def home: Action[AnyContent] = Action {
    Redirect(configuration.get[String]("play.http.context").stripSuffix("/") + "/index.html")
  }
}