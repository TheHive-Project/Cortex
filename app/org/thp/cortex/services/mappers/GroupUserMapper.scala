package org.thp.cortex.services.mappers

import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.WSClient

import javax.inject.Inject

import org.elastic4play.AuthenticationError
import org.elastic4play.controllers.Fields

class GroupUserMapper(
    loginAttrName: String,
    nameAttrName: String,
    groupAttrName: String,
    organizationAttrName: Option[String],
    defaultRoles: Seq[String],
    defaultOrganization: Option[String],
    groupsUrl: String,
    mappings: Map[String, Seq[String]],
    ws: WSClient,
    implicit val ec: ExecutionContext
) extends UserMapper {

  @Inject() def this(configuration: Configuration, ws: WSClient, ec: ExecutionContext) =
    this(
      configuration.getOptional[String]("auth.sso.attributes.login").getOrElse("login"),
      configuration.getOptional[String]("auth.sso.attributes.name").getOrElse("name"),
      configuration.getOptional[String]("auth.sso.attributes.groups").getOrElse(""),
      configuration.getOptional[String]("auth.sso.attributes.organization"),
      configuration.getOptional[Seq[String]]("auth.sso.defaultRoles").getOrElse(Seq()),
      configuration.getOptional[String]("auth.sso.defaultOrganization"),
      configuration.getOptional[String]("auth.sso.groups.url").getOrElse(""),
      configuration.getOptional[Map[String, Seq[String]]]("auth.sso.groups.mappings").getOrElse(Map()),
      ws,
      ec
    )

  override val name: String = "group"

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] = {

    val apiCall = authHeader.fold(ws.url(groupsUrl))(headers => ws.url(groupsUrl).addHttpHeaders(headers))
    apiCall.get.flatMap { r =>
      val jsonGroups  = (r.json \ groupAttrName).as[Seq[String]]
      val mappedRoles = jsonGroups.flatMap(mappings.get).maxBy(_.length)
      val roles       = if (mappedRoles.nonEmpty) mappedRoles else defaultRoles

      val fields = for {
        login <- (jsValue \ loginAttrName).validate[String]
        name  <- (jsValue \ nameAttrName).validate[String]
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
}
