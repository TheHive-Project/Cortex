package org.thp.cortex.services

import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.{BarcodeFormat, EncodeHintType}
import com.warrenstrange.googleauth.{GoogleAuthenticator, GoogleAuthenticatorConfig}
import org.elastic4play.controllers.Fields
import org.elastic4play.services.AuthContext
import org.elastic4play.utils.Hasher
import org.thp.cortex.models.{MultiFactorCodeInvalid, MultiFactorCodeRequired, User}
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsArray, JsNull, JsString}
import play.api.{Configuration, Logger}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object TOTPSrv {

  /** Alphabet for backup codes: Crockford-ish base32 without the characters users misread (I, L, O, U, 0, 1). */
  private[services] val backupCodeAlphabet: String = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

  private[services] val backupCodeLength: Int = 10

  /** Authenticator apps and password managers render codes as "123 456"; operators paste backup codes as "ABCDE-FGHIJ". */
  private[services] def normalizeCode(code: String): String = code.replaceAll("[\\s-]", "").toUpperCase

  /** A TOTP code is 6 digits. Anything else is either a backup code or garbage. */
  private[services] def asTotpCode(code: String): Option[Int] = {
    val normalized = normalizeCode(code)
    if (normalized.length == 6 && normalized.forall(_.isDigit)) Try(normalized.toInt).toOption
    else None
  }

  /** Backup codes are stored the way passwords are: "<seed>,<sha256(seed + code)>". */
  private[services] def hashBackupCode(seed: String, code: String): String =
    seed + "," + Hasher("SHA-256").fromString(seed + normalizeCode(code)).head.toString

  private[services] def backupCodeMatches(storedHash: String, code: String): Boolean =
    storedHash.split(",", 2) match {
      case Array(seed, _) =>
        MessageDigest.isEqual(
          hashBackupCode(seed, code).getBytes(StandardCharsets.UTF_8),
          storedHash.getBytes(StandardCharsets.UTF_8)
        )
      case _ => false
    }

  /** Human-readable form of a backup code, shown once at enrolment. */
  private[services] def formatBackupCode(code: String): String = code.grouped(5).mkString("-")

  /** RFC 6238 / Key-Uri-Format. The label is "issuer:account" and issuer is repeated as a parameter. */
  private[services] def otpAuthUri(issuer: String, account: String, secret: String): String = {
    def enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name).replace("+", "%20")
    s"otpauth://totp/${enc(issuer)}:${enc(account)}" +
      s"?secret=$secret&issuer=${enc(issuer)}&algorithm=SHA1&digits=6&period=30"
  }

  /** Renders the QR as an SVG rather than a bitmap: zxing's raster writers live in the "javase"
    * module, which drags in jai-imageio, and an SVG scales to whatever the browser needs anyway.
    * Dark modules are emitted as horizontal runs to keep the document small.
    */
  private[services] def qrCodeSvg(content: String, quietZone: Int = 2): String = {
    val hints = new java.util.EnumMap[EncodeHintType, Any](classOf[EncodeHintType])
    hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name)
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
    hints.put(EncodeHintType.MARGIN, Int.box(0))

    val matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size   = matrix.getWidth
    val side   = size + 2 * quietZone

    val runs = new StringBuilder
    for (y <- 0 until size) {
      var x = 0
      while (x < size) {
        if (matrix.get(x, y)) {
          val start = x
          while (x < size && matrix.get(x, y)) x += 1
          runs.append(s"M${start + quietZone} ${y + quietZone}h${x - start}v1h-${x - start}z")
        } else x += 1
      }
    }

    s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $side $side" shape-rendering="crispEdges">""" +
      s"""<rect width="$side" height="$side" fill="#ffffff"/>""" +
      s"""<path fill="#000000" d="$runs"/></svg>"""
  }

  /** Inline-able by an <img> tag, which is how the UI already renders avatars. */
  private[services] def qrCodeDataUri(content: String): String =
    "data:image/svg+xml;base64," + Base64.getEncoder.encodeToString(qrCodeSvg(content).getBytes(StandardCharsets.UTF_8))
}

/** TOTP (RFC 6238) multi-factor authentication.
  *
  * Deliberately *not* an `AuthSrv`: `Module.scala` reflectively binds every concrete `AuthSrv` into the provider list, and this is a gate applied
  * on top of whichever provider authenticated the password, not a provider of its own.
  */
@Singleton
class TOTPSrv @Inject() (
    configuration: Configuration,
    userSrv: UserSrv,
    cache: AsyncCacheApi,
    implicit val ec: ExecutionContext
) {
  import TOTPSrv._

  private[TOTPSrv] lazy val logger = Logger(getClass)

  val enabled: Boolean = configuration.getOptional[Boolean]("auth.multifactor.enabled").getOrElse(true)
  val issuer: String   = configuration.getOptional[String]("auth.multifactor.issuer").getOrElse("Cortex")

  /** Number of 30s windows accepted, centred on now: 3 tolerates one step of clock drift either way. */
  private val windowSize: Int      = configuration.getOptional[Int]("auth.multifactor.windowSize").getOrElse(3)
  private val backupCodeCount: Int = configuration.getOptional[Int]("auth.multifactor.backupCodes").getOrElse(10)

  /** A 6-digit code with a 3-window tolerance leaves ~333k guesses, so the attempt counter is what
    * actually makes the second factor worth having. It is per-instance (caffeine), not cluster-wide.
    */
  private val maxAttempts: Int = configuration.getOptional[Int]("auth.multifactor.maxAttempts").getOrElse(5)

  private val lockoutDuration: FiniteDuration = configuration
    .getOptional[Duration]("auth.multifactor.lockoutDuration")
    .collect { case fd: FiniteDuration => fd }
    .getOrElse(15.minutes)

  private val random = new SecureRandom()

  private val googleAuthenticator = new GoogleAuthenticator(
    new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
      .setWindowSize(windowSize)
      .setNumberOfScratchCodes(0) // backup codes are generated and hashed here, not by the library
      .build()
  )

  def generateSecret(): String = googleAuthenticator.createCredentials().getKey

  def otpAuthUri(account: String, secret: String): String = TOTPSrv.otpAuthUri(issuer, account, secret)

  /** The enrolment QR, ready to drop into an <img src>. */
  def qrCode(account: String, secret: String): String = TOTPSrv.qrCodeDataUri(otpAuthUri(account, secret))

  /** Returns the plaintext codes to show the user once, paired with the hashes to persist. */
  def generateBackupCodes(): (Seq[String], Seq[String]) = {
    val codes = Seq.fill(backupCodeCount) {
      val raw = Seq.fill(backupCodeLength)(backupCodeAlphabet.charAt(random.nextInt(backupCodeAlphabet.length))).mkString
      formatBackupCode(raw)
    }
    val hashes = codes.map { code =>
      val seed = Seq.fill(10)(backupCodeAlphabet.charAt(random.nextInt(backupCodeAlphabet.length))).mkString
      hashBackupCode(seed, code)
    }
    codes -> hashes
  }

  def isEnrolled(user: User): Boolean = user.totpSecret().isDefined

  private def attemptKey(userId: String) = s"mfa-attempts-$userId"

  private def checkNotLockedOut(userId: String): Future[Unit] =
    cache.get[Int](attemptKey(userId)).flatMap {
      case Some(attempts) if attempts >= maxAttempts =>
        logger.warn(s"MFA verification refused for user $userId: too many failed attempts")
        Future.failed(MultiFactorCodeInvalid(s"Too many invalid codes, try again in ${lockoutDuration.toMinutes} minutes"))
      case _ => Future.successful(())
    }

  private def registerFailure(userId: String): Future[Unit] =
    cache.get[Int](attemptKey(userId)).flatMap { attempts =>
      cache.set(attemptKey(userId), attempts.getOrElse(0) + 1, lockoutDuration).map(_ => ())
    }

  private def resetFailures(userId: String): Future[Unit] = cache.remove(attemptKey(userId)).map(_ => ())

  /** Verifies a code against a secret only. Used during enrolment, before anything is persisted. */
  def verifyCode(secret: String, code: String): Boolean =
    asTotpCode(code).exists(totpCode => Try(googleAuthenticator.authorize(secret, totpCode)).getOrElse(false))

  /** Consumes a backup code if it matches, returning the remaining hashes. */
  private def burnBackupCode(user: User, code: String): Option[Seq[String]] = {
    val stored = user.totpScratchCodes()
    stored.find(backupCodeMatches(_, code)).map(matched => stored.filterNot(_ == matched))
  }

  /** The login-time gate. Succeeds immediately when MFA is off or the user isn't enrolled. */
  def checkAtLogin(user: User, code: Option[String]): Future[Unit] =
    if (!enabled) Future.successful(())
    else
      user.totpSecret() match {
        case None => Future.successful(()) // not enrolled: MFA is opt-in, so this is a normal login
        case Some(secret) =>
          code.map(_.trim).filter(_.nonEmpty) match {
            case None => Future.failed(MultiFactorCodeRequired())
            case Some(submitted) =>
              checkNotLockedOut(user.id).flatMap { _ =>
                if (verifyCode(secret, submitted)) resetFailures(user.id)
                else
                  burnBackupCode(user, submitted) match {
                    case Some(remaining) =>
                      logger.info(s"User ${user.id} used an MFA backup code, ${remaining.size} left")
                      userSrv
                        .inInitAuthContext { implicit authContext: AuthContext =>
                          userSrv.update(user.id, Fields.empty.set("totpScratchCodes", JsArray(remaining.map(JsString.apply))))
                        }
                        .flatMap(_ => resetFailures(user.id))
                    case None =>
                      registerFailure(user.id).flatMap(_ => Future.failed(MultiFactorCodeInvalid()))
                  }
              }
          }
      }

  /** Enrolment: persists the secret and the backup code hashes once the user has proven possession. */
  def enable(userId: String, secret: String, backupCodeHashes: Seq[String])(implicit authContext: AuthContext): Future[Unit] =
    userSrv
      .update(
        userId,
        Fields
          .empty
          .set("totpSecret", JsString(secret))
          .set("totpScratchCodes", JsArray(backupCodeHashes.map(JsString.apply)))
      )
      .flatMap(_ => resetFailures(userId))

  def disable(userId: String)(implicit authContext: AuthContext): Future[Unit] =
    userSrv
      .update(
        userId,
        Fields
          .empty
          .set("totpSecret", JsNull)          // JsNull unsets an optional attribute...
          .set("totpScratchCodes", JsArray()) // ...while an empty array empties a multi-valued one
      )
      .flatMap(_ => resetFailures(userId))

  /** Guarded verification used by the enrolment confirmation, so enrolment can't be used as an unthrottled oracle. */
  def verifyForEnrolment(userId: String, secret: String, code: String): Future[Unit] =
    checkNotLockedOut(userId).flatMap { _ =>
      if (verifyCode(secret, code)) resetFailures(userId)
      else registerFailure(userId).flatMap(_ => Future.failed(MultiFactorCodeInvalid()))
    }
}
