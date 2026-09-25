package org.thp.cortex.services.auth.oauth2

import play.api.Configuration

final private[oauth2] case class OAuth2Config(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    responseType: String,
    grantType: String,
    authorizationUrl: String,
    tokenUrl: String,
    userUrl: String,
    scope: Seq[String],
    authorizationHeader: String,
    autoupdate: Boolean,
    autocreate: Boolean
)

private[oauth2] object OAuth2Config {

  def apply(configuration: Configuration): Option[OAuth2Config] =
    for {
      clientId     <- configuration.getOptional[String]("auth.oauth2.clientId")
      clientSecret <- configuration.getOptional[String]("auth.oauth2.clientSecret")
      redirectUri  <- configuration.getOptional[String]("auth.oauth2.redirectUri")
      responseType <- configuration.getOptional[String]("auth.oauth2.responseType")
      grantType = configuration.getOptional[String]("auth.oauth2.grantType").getOrElse("authorization_code")
      authorizationUrl <- configuration.getOptional[String]("auth.oauth2.authorizationUrl")
      tokenUrl         <- configuration.getOptional[String]("auth.oauth2.tokenUrl")
      userUrl          <- configuration.getOptional[String]("auth.oauth2.userUrl")
      scope            <- configuration.getOptional[Seq[String]]("auth.oauth2.scope")
      authorizationHeader = configuration.getOptional[String]("auth.oauth2.authorizationHeader").getOrElse("Bearer")
      autocreate          = configuration.getOptional[Boolean]("auth.sso.autocreate").getOrElse(false)
      autoupdate          = configuration.getOptional[Boolean]("auth.sso.autoupdate").getOrElse(false)
    } yield OAuth2Config(
      clientId,
      clientSecret,
      redirectUri,
      responseType,
      grantType,
      authorizationUrl,
      tokenUrl,
      userUrl,
      scope,
      authorizationHeader,
      autocreate,
      autoupdate
    )
}
