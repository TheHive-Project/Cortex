package org.thp.cortex.services.mappers

import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration
import play.api.libs.json._

import javax.inject.Inject

import org.elastic4play.AuthenticationError
import org.elastic4play.controllers.Fields

class SimpleUserMapper(
    loginAttrName: String,
    nameAttrName: String,
    rolesAttrName: Option[String],
    organizationAttrName: Option[String],
    defaultRoles: Seq[String],
    defaultOrganization: Option[String],
    implicit val ec: ExecutionContext
) extends UserMapper {

  @Inject() def this(configuration: Configuration, ec: ExecutionContext) =
    this(
      configuration.getOptional[String]("auth.sso.attributes.login").getOrElse("login"),
      configuration.getOptional[String]("auth.sso.attributes.name").getOrElse("name"),
      configuration.getOptional[String]("auth.sso.attributes.roles"),
      configuration.getOptional[String]("auth.sso.attributes.organization"),
      configuration.getOptional[Seq[String]]("auth.sso.defaultRoles").getOrElse(Seq()),
      configuration.getOptional[String]("auth.sso.defaultOrganization"),
      ec
    )

  override val name: String = "simple"

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] = {
    val fields = for {
      login <- (jsValue \ loginAttrName).validate[String]
      name  <- (jsValue \ nameAttrName).validate[String]
      roles = rolesAttrName.fold(defaultRoles)(r => (jsValue \ r).asOpt[Seq[String]].getOrElse(defaultRoles))
      organization <- organizationAttrName
        .flatMap(o => (jsValue \ o).asOpt[String])
        .orElse(defaultOrganization)
        .fold[JsResult[String]](JsError())(o => JsSuccess(o))
    } yield Fields(Json.obj("login" -> login.toLowerCase, "name" -> name, "roles" -> roles, "organization" -> organization))
    fields match {
      case JsSuccess(f, _) => Future.successful(f)
      case JsError(errors) => Future.failed(AuthenticationError(s"User info fails: ${errors.map(_._1).mkString}"))
    }
  }
}
