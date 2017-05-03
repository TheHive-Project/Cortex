package services

import javax.inject.Inject

import models.JsonFormat.dataActifactReads
import models.{ DataArtifact, FileArtifact }
import org.apache.commons.codec.binary.Base64
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsValue, Json }

import scala.concurrent.{ ExecutionContext, Future }

class MispSrv @Inject() (analyzerSrv: AnalyzerSrv) {
  private[MispSrv] lazy val logger = Logger(getClass)

  def moduleList: JsValue = {
    JsArray(analyzerSrv.list.map { analyzer ⇒
      Json.obj(
        "name" → analyzer.id,
        "type" → "cortex",
        "mispattributes" → Json.obj(
          "input" → analyzer.dataTypeList.flatMap(dataType2mispType).distinct,
          "output" → Json.arr()),
        "meta" → Json.obj(
          "module-type" → Json.arr("cortex"),
          "description" → analyzer.description,
          "author" → analyzer.author,
          "version" → analyzer.version,
          "config" → Json.arr()))
    })
  }

  def query(module: String, mispType: String, data: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    analyzerSrv.get(module).map { analyzer ⇒
      val artifact = mispType2dataType(mispType) match {
        case "file" if mispType == "malware-sample" ⇒ ???
        case "file" ⇒ FileArtifact(Base64.decodeBase64(data), Json.obj(
          "tlp" → 1,
          "dataType" → "file"))
        case dataType ⇒ DataArtifact(data, Json.obj(
          "tlp" → 1,
          "dataType" → dataType))
      }

      analyzer
        .analyze(artifact)
        .map { output ⇒
          logger.info(s"analyzer output:\n$output")
          val success = (output \ "success")
            .asOpt[Boolean]
            .getOrElse(false)
          if (success) {
            Json.obj(
              "results" → ((output \ "artifacts")
                .asOpt[Seq[JsObject]]
                .getOrElse(Nil)
                .map { artifact ⇒
                  Json.obj(
                    "types" → dataType2mispType((artifact \ "type").as[String]),
                    "values" → Json.arr((artifact \ "value").as[String]))
                }
                :+ Json.obj(
                  "types" → Json.arr("cortex"),
                  "values" → Json.arr(output.toString))))
          }
          else {
            val message = (output \ "error").asOpt[String].getOrElse(output.toString)
            Json.obj(
              "error" → message)
          }
        }
    }
      .getOrElse(Future.failed(new Exception(s"Module $module not found")))
  }

  def mispType2dataType(mispType: String): String = typeLookup.getOrElse(mispType, {
    logger.warn(s"Misp type $mispType not recognized")
    "other"
  })

  def dataType2mispType(dataType: String): Seq[String] = {
    val mispTypes = typeLookup.filter(_._2 == dataType)
      .keys
      .toSeq
      .distinct

    if (mispTypes.isEmpty) {
      logger.warn(s"Data type $dataType not recognized")
      Seq("other")
    }
    else mispTypes
  }

  private val typeLookup = Map(
    "md5" → "hash",
    "sha1" → "hash",
    "sha256" → "hash",
    "filename" → "filename",
    "pdb" → "other",
    "filename|md5" → "other",
    "filename|sha1" → "other",
    "filename|sha256" → "other",
    "ip-src" → "ip",
    "ip-dst" → "ip",
    "hostname" → "fqdn",
    "domain" → "domain",
    "domain|ip" → "other",
    "email-src" → "mail",
    "email-dst" → "mail",
    "email-subject" → "mail_subject",
    "email-attachment" → "other",
    "float" → "other",
    "url" → "url",
    "http-method" → "other",
    "user-agent" → "user-agent",
    "regkey" → "registry",
    "regkey|value" → "registry",
    "AS" → "other",
    "snort" → "other",
    "pattern-in-file" → "other",
    "pattern-in-traffic" → "other",
    "pattern-in-memory" → "other",
    "yara" → "other",
    "sigma" → "other",
    "vulnerability" → "other",
    "attachment" → "file",
    "malware-sample" → "file",
    "link" → "other",
    "comment" → "other",
    "text" → "other",
    "hex" → "other",
    "other" → "other",
    "named" → "other",
    "mutex" → "other",
    "target-user" → "other",
    "target-email" → "mail",
    "target-machine" → "hostname",
    "target-org" → "other",
    "target-location" → "other",
    "target-external" → "other",
    "btc" → "other",
    "iban" → "other",
    "bic" → "other",
    "bank-account-nr" → "other",
    "aba-rtn" → "other",
    "bin" → "other",
    "cc-number" → "other",
    "prtn" → "other",
    "threat-actor" → "other",
    "campaign-name" → "other",
    "campaign-id" → "other",
    "malware-type" → "other",
    "uri" → "uri_path",
    "authentihash" → "other",
    "ssdeep" → "hash",
    "imphash" → "hash",
    "pehash" → "hash",
    "impfuzzy" → "hash",
    "sha224" → "hash",
    "sha384" → "hash",
    "sha512" → "hash",
    "sha512/224" → "hash",
    "sha512/256" → "hash",
    "tlsh" → "other",
    "filename|authentihash" → "other",
    "filename|ssdeep" → "other",
    "filename|imphash" → "other",
    "filename|impfuzzy" → "other",
    "filename|pehash" → "other",
    "filename|sha224" → "other",
    "filename|sha384" → "other",
    "filename|sha512" → "other",
    "filename|sha512/224" → "other",
    "filename|sha512/256" → "other",
    "filename|tlsh" → "other",
    "windows-scheduled-task" → "other",
    "windows-service-name" → "other",
    "windows-service-displayname" → "other",
    "whois-registrant-email" → "mail",
    "whois-registrant-phone" → "other",
    "whois-registrant-name" → "other",
    "whois-registrar" → "other",
    "whois-creation-date" → "other",
    "x509-fingerprint-sha1" → "other",
    "dns-soa-email" → "other",
    "size-in-bytes" → "other",
    "counter" → "other",
    "datetime" → "other",
    "cpe" → "other",
    "port" → "other",
    "ip-dst|port" → "other",
    "ip-src|port" → "other",
    "hostname|port" → "other",
    "email-dst-display-name" → "other",
    "email-src-display-name" → "other",
    "email-header" → "other",
    "email-reply-to" → "other",
    "email-x-mailer" → "other",
    "email-mime-boundary" → "other",
    "email-thread-index" → "other",
    "email-message-id" → "other",
    "github-username" → "other",
    "github-repository" → "other",
    "github-organisation" → "other",
    "jabber-id" → "other",
    "twitter-id" → "other",
    "first-name" → "other",
    "middle-name" → "other",
    "last-name" → "other",
    "date-of-birth" → "other",
    "place-of-birth" → "other",
    "gender" → "other",
    "passport-number" → "other",
    "passport-country" → "other",
    "passport-expiration" → "other",
    "redress-number" → "other",
    "nationality" → "other",
    "visa-number" → "other",
    "issue-date-of-the-visa" → "other",
    "primary-residence" → "other",
    "country-of-residence" → "other",
    "special-service-request" → "other",
    "frequent-flyer-number" → "other",
    "travel-details" → "other",
    "payment-details" → "other",
    "place-port-of-original-embarkation" → "other",
    "place-port-of-clearance" → "other",
    "place-port-of-onward-foreign-destination" → "other",
    "passenger-name-record-locator-number" → "other",
    "mobile-application-id" → "other")
}