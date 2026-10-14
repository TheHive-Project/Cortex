package org.thp.cortex.services

import play.api.mvc.RequestHeader

/** Request URI as written in logs: the whole URI, except for the SSO callback, whose query string is not logged.
  * The SSO callback is the only request where Cortex expects sensitive values in the query string: the authorization server
  * can only send the OAuth2 authorization code and state back through the URL of the browser redirection (RFC 6749 section 4.1.2).
  */
private[services] object LoggedUri {

  def apply(request: RequestHeader): String =
    if (request.path.endsWith("/api/ssoLogin")) request.path else request.uri
}
