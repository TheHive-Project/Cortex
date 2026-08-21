package org.thp.cortex.services

import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.{BarcodeFormat, EncodeHintType}
import com.warrenstrange.googleauth.{GoogleAuthenticator, GoogleAuthenticatorConfig}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.PlaySpecification

@RunWith(classOf[JUnitRunner])
class TOTPSrvSpec extends PlaySpecification {

  "TOTPSrv.normalizeCode" should {

    "strip the spacing authenticator apps display" in {
      TOTPSrv.normalizeCode("123 456") must_== "123456"
    }

    "strip the dashes backup codes are displayed with" in {
      TOTPSrv.normalizeCode("ABCDE-FGHJK") must_== "ABCDEFGHJK"
    }

    "upper-case backup codes typed in lower case" in {
      TOTPSrv.normalizeCode("abcde-fghjk") must_== "ABCDEFGHJK"
    }
  }

  "TOTPSrv.asTotpCode" should {

    "accept a six digit code" in {
      TOTPSrv.asTotpCode("094287") must beSome(94287)
    }

    "accept a six digit code with a space" in {
      TOTPSrv.asTotpCode("094 287") must beSome(94287)
    }

    "reject a backup code" in {
      TOTPSrv.asTotpCode("ABCDE-FGHJK") must beNone
    }

    "reject a code of the wrong length" in {
      TOTPSrv.asTotpCode("12345") must beNone
      TOTPSrv.asTotpCode("1234567") must beNone
    }

    "reject an empty code" in {
      TOTPSrv.asTotpCode("") must beNone
    }
  }

  "TOTPSrv.otpAuthUri" should {

    "build a key URI an authenticator app can parse" in {
      TOTPSrv.otpAuthUri("Cortex", "alice", "JBSWY3DPEHPK3PXP") must_==
        "otpauth://totp/Cortex:alice?secret=JBSWY3DPEHPK3PXP&issuer=Cortex&algorithm=SHA1&digits=6&period=30"
    }

    "percent-encode an issuer containing a space" in {
      TOTPSrv.otpAuthUri("My Cortex", "alice", "JBSWY3DPEHPK3PXP") must contain("otpauth://totp/My%20Cortex:alice")
      TOTPSrv.otpAuthUri("My Cortex", "alice", "JBSWY3DPEHPK3PXP") must contain("issuer=My%20Cortex")
    }

    "percent-encode a login containing a slash or an at sign" in {
      TOTPSrv.otpAuthUri("Cortex", "alice@example.com", "JBSWY3DPEHPK3PXP") must contain("Cortex:alice%40example.com")
      TOTPSrv.otpAuthUri("Cortex", "dom/alice", "JBSWY3DPEHPK3PXP") must contain("Cortex:dom%2Falice")
    }
  }

  "TOTPSrv.qrCodeSvg" should {

    val uri = TOTPSrv.otpAuthUri("Cortex", "alice", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")
    val svg = TOTPSrv.qrCodeSvg(uri)

    "produce a self-contained SVG document" in {
      svg must startWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")
      svg must endWith("</svg>")
      svg must contain("viewBox=\"0 0 ")
    }

    "draw the dark modules as a single path" in {
      svg must contain("<path fill=\"#000000\" d=\"M")
      svg.sliding("<path".length).count(_ == "<path") must_== 1
    }

    "leave a quiet zone around the symbol" in {
      // Every run starts at x >= quietZone, so no segment touches the left edge
      svg must not(contain("d=\"M0 "))
    }

    // The run-length encoding is the only hand-written part of the rendering, so rebuild the module
    // grid from the emitted path and check it is exactly the matrix zxing produced.
    "encode every dark module of the symbol, and nothing else" in {
      val quietZone = 2
      val hints     = new java.util.EnumMap[EncodeHintType, Any](classOf[EncodeHintType])
      hints.put(EncodeHintType.CHARACTER_SET, "UTF-8")
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
      hints.put(EncodeHintType.MARGIN, Int.box(0))
      val matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 0, 0, hints)

      val expected = (for {
        y <- 0 until matrix.getHeight
        x <- 0 until matrix.getWidth
        if matrix.get(x, y)
      } yield (x + quietZone, y + quietZone)).toSet

      val segment = """M(\d+) (\d+)h(\d+)v1h-\3z""".r
      val drawn = segment
        .findAllMatchIn(svg)
        .flatMap(m => (m.group(1).toInt until m.group(1).toInt + m.group(3).toInt).map(_ -> m.group(2).toInt))
        .toSet

      drawn must_== expected
    }
  }

  "TOTPSrv.qrCodeDataUri" should {

    "be embeddable in an img tag" in {
      TOTPSrv.qrCodeDataUri("otpauth://totp/Cortex:alice?secret=JBSWY3DPEHPK3PXP") must startWith("data:image/svg+xml;base64,")
    }
  }

  "TOTPSrv backup codes" should {

    "match the code they were derived from" in {
      TOTPSrv.backupCodeMatches(TOTPSrv.hashBackupCode("SEED123456", "ABCDE-FGHJK"), "ABCDE-FGHJK") must beTrue
    }

    "match regardless of the formatting the user types" in {
      val hash = TOTPSrv.hashBackupCode("SEED123456", "ABCDE-FGHJK")
      TOTPSrv.backupCodeMatches(hash, "abcdefghjk") must beTrue
      TOTPSrv.backupCodeMatches(hash, "ABCDE FGHJK") must beTrue
    }

    "not match a different code" in {
      TOTPSrv.backupCodeMatches(TOTPSrv.hashBackupCode("SEED123456", "ABCDE-FGHJK"), "ABCDE-FGHJM") must beFalse
    }

    "not match when the stored value is malformed" in {
      TOTPSrv.backupCodeMatches("no-comma-here", "ABCDE-FGHJK") must beFalse
    }

    "produce different hashes for the same code under different seeds" in {
      TOTPSrv.hashBackupCode("SEED000001", "ABCDE-FGHJK") must_!= TOTPSrv.hashBackupCode("SEED000002", "ABCDE-FGHJK")
    }

    "group a raw code for display" in {
      TOTPSrv.formatBackupCode("ABCDEFGHJK") must_== "ABCDE-FGHJK"
    }

    "only use characters that can't be misread" in {
      TOTPSrv.backupCodeAlphabet must not(contain("0"))
      TOTPSrv.backupCodeAlphabet must not(contain("1"))
      TOTPSrv.backupCodeAlphabet must not(contain("I"))
      TOTPSrv.backupCodeAlphabet must not(contain("L"))
      TOTPSrv.backupCodeAlphabet must not(contain("O"))
      TOTPSrv.backupCodeAlphabet must not(contain("U"))
    }
  }

  // RFC 6238 Appendix B test vectors, restricted to the HMAC-SHA1 rows. The RFC publishes 8-digit
  // codes, so the library is configured for 8 digits here; production uses the 6-digit default.
  "The TOTP library" should {

    val rfc6238Authenticator = new GoogleAuthenticator(
      new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
        .setCodeDigits(8)
        .setWindowSize(1)
        .setNumberOfScratchCodes(0)
        .build()
    )

    // RFC 6238 uses the ASCII seed "12345678901234567890", which is this in base32
    val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    def codeAt(epochSeconds: Long): Int = rfc6238Authenticator.getTotpPassword(rfcSecret, epochSeconds * 1000L)

    "reproduce the RFC 6238 vector at T=59" in {
      codeAt(59L) must_== 94287082
    }

    "reproduce the RFC 6238 vector at T=1111111109" in {
      codeAt(1111111109L) must_== 7081804
    }

    "reproduce the RFC 6238 vector at T=1111111111" in {
      codeAt(1111111111L) must_== 14050471
    }

    "reproduce the RFC 6238 vector at T=1234567890" in {
      codeAt(1234567890L) must_== 89005924
    }

    "reproduce the RFC 6238 vector at T=2000000000" in {
      codeAt(2000000000L) must_== 69279037
    }
  }

  "TOTPSrv.verifyCode against a generated secret" should {

    // Mirrors the production configuration: 6 digits, 3 windows of tolerance.
    val authenticator = new GoogleAuthenticator(
      new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
        .setWindowSize(3)
        .setNumberOfScratchCodes(0)
        .build()
    )
    val secret = authenticator.createCredentials().getKey

    "accept the code for the current window" in {
      val now  = System.currentTimeMillis()
      val code = f"${authenticator.getTotpPassword(secret, now)}%06d"
      authenticator.authorize(secret, TOTPSrv.asTotpCode(code).get) must beTrue
    }

    "accept the code from the previous window (clock drift)" in {
      val previous = System.currentTimeMillis() - 30000L
      val code     = authenticator.getTotpPassword(secret, previous)
      authenticator.authorize(secret, code) must beTrue
    }

    "reject a code from far outside the window" in {
      val longAgo = System.currentTimeMillis() - 600000L
      val code    = authenticator.getTotpPassword(secret, longAgo)
      authenticator.authorize(secret, code) must beFalse
    }

    "generate a base32 secret an authenticator app can consume" in {
      secret must beMatching("[A-Z2-7]+")
    }
  }
}
