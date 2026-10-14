package org.thp.cortex.services.auth.oauth2

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64

/** PKCE (RFC 7636): code verifier and its S256 challenge
  */
private[oauth2] object Pkce {

  private val secureRandom = new SecureRandom()

  /** Random code verifier: 32 bytes of entropy, base64url-encoded without padding (43 characters, RFC 7636 section 4.1)
    */
  def generateCodeVerifier(): String = {
    val bytes = new Array[Byte](32)
    secureRandom.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
  }

  /** S256 code challenge: BASE64URL-ENCODE(SHA256(ASCII(code_verifier))) (RFC 7636 section 4.2)
    */
  def computeCodeChallenge(codeVerifier: String): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII))
    Base64.getUrlEncoder.withoutPadding.encodeToString(digest)
  }
}
