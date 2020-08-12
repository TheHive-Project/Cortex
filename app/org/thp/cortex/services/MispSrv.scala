package org.thp.cortex.services

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

import play.api.Logger
import play.api.libs.json.{Json, _}

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import javax.inject.{Inject, Named, Singleton}
import org.apache.commons.codec.binary.Base64
import org.thp.cortex.models._
import org.thp.cortex.services.AuditActor.Register

import org.elastic4play.NotFoundError
import org.elastic4play.services._

@Singleton
class MispSrv @Inject() (
    workerSrv: WorkerSrv,
    attachmentSrv: AttachmentSrv,
    jobSrv: JobSrv,
    @Named("audit") auditActor: ActorRef,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  private[MispSrv] lazy val logger = Logger(getClass)

  def moduleList(implicit authContext: AuthContext): (Source[JsObject, NotUsed], Future[Long]) = {
    val (analyzers, analyzerCount) = workerSrv.findAnalyzersForUser(authContext.userId, QueryDSL.any, Some("all"), Nil)

    val mispAnalyzers = analyzers
      .map { analyzer =>
        Json.obj(
          "name"           -> analyzer.name(),
          "type"           -> "cortex",
          "mispattributes" -> Json.obj("input" -> analyzer.dataTypeList().flatMap(dataType2mispType).distinct, "output" -> Json.arr()),
          "meta" -> Json.obj(
            "module-type" -> Json.arr("cortex"),
            "description" -> analyzer.description(),
            "author"      -> analyzer.author(),
            "version"     -> analyzer.vers(),
            "config"      -> Json.arr()
          )
        )
      }
    mispAnalyzers -> analyzerCount
  }

  def query(module: String, mispType: String, data: String)(implicit authContext: AuthContext): Future[JsObject] = {
    import org.elastic4play.services.QueryDSL._

    val artifact: Either[String, Attachment] = toArtifact(mispType, data)
    val duration                             = 20.minutes // TODO configurable

    for {
      analyzer <- workerSrv.findAnalyzersForUser(authContext.userId, "name" ~= module, Some("0-1"), Nil)._1.runWith(Sink.headOption)
      job <- analyzer
        .map(jobSrv.create(_, mispType2dataType(mispType), artifact, 0, 0, "", JsObject.empty, None, force = false))
        .getOrElse(Future.failed(NotFoundError(s"Module $module not found")))
      _          <- auditActor.ask(Register(job.id, duration))(Timeout(duration))
      updatedJob <- jobSrv.getForUser(authContext.userId, job.id)
      mispOutput <- toMispOutput(authContext.userId, updatedJob)
    } yield mispOutput
  }

  private def toMispOutput(userId: String, job: Job): Future[JsObject] =
    job.status() match {
      case JobStatus.Success =>
        for {
          report <- jobSrv.getReport(job)
          artifacts <- jobSrv
            .findArtifacts(userId, job.id, QueryDSL.any, Some("all"), Nil)
            ._1
            .map { artifact =>
              toMispOutput(artifact)
            }
            .runWith(Sink.seq)
          reportJson      = Json.obj("full"  -> report.full(), "summary"     -> report.summary())
          cortexAttribute = Json.obj("types" -> Json.arr("cortex"), "values" -> Json.arr(reportJson.toString))
        } yield Json.obj("results" -> (artifacts :+ cortexAttribute))
      case JobStatus.Waiting => Future.successful(Json.obj("error" -> "This job hasn't finished yet"))
      case JobStatus.Deleted => Future.successful(Json.obj("error" -> "This job has been deleted"))
      case JobStatus.Failure =>
        val message = job.errorMessage().getOrElse("This job has failed, without message!")
        Future.successful(Json.obj("error" -> message))
      case JobStatus.InProgress => Future.successful(Json.obj("error" -> "This job hasn't finished yet"))
    }

  private def toArtifact(mispType: String, data: String): Either[String, Attachment] =
    mispType2dataType(mispType) match {
      case "file" if mispType == "malware-sample" => ??? // TODO
      case "file" =>
        val FAttachment = attachmentSrv.save("noname", "application/octet-stream", Base64.decodeBase64(data)).map { a =>
          a
        }
        Right(Await.result(FAttachment, 10.seconds))
      case _ => Left(data)
    }

  private def toMispOutput(artifact: Artifact): JsObject =
    (artifact.data(), artifact.attachment()) match {
      case (Some(data), None) => Json.obj("types" -> dataType2mispType(artifact.dataType()), "values" -> Json.arr(data))
      //case (None, Some(_)) ⇒ ???
      case _ => ???
    }

  private def mispType2dataType(mispType: String): String =
    typeLookup.getOrElse(mispType, {
      logger.warn(s"Misp type $mispType not recognized")
      "other"
    })

  private def dataType2mispType(dataType: String): Seq[String] = {
    val mispTypes = typeLookup
      .filter(_._2 == dataType)
      .keys
      .toSeq
      .distinct

    if (mispTypes.isEmpty) {
      logger.warn(s"Data type $dataType not recognized")
      Seq("other")
    } else mispTypes
  }

  private val typeLookup = Map(
    "md5"                                      -> "hash",
    "sha1"                                     -> "hash",
    "sha256"                                   -> "hash",
    "filename"                                 -> "filename",
    "pdb"                                      -> "other",
    "filename|md5"                             -> "other",
    "filename|sha1"                            -> "other",
    "filename|sha256"                          -> "other",
    "ip-src"                                   -> "ip",
    "ip-dst"                                   -> "ip",
    "hostname"                                 -> "fqdn",
    "domain"                                   -> "domain",
    "domain|ip"                                -> "other",
    "email-src"                                -> "mail",
    "email-dst"                                -> "mail",
    "email-subject"                            -> "mail_subject",
    "email-attachment"                         -> "other",
    "float"                                    -> "other",
    "url"                                      -> "url",
    "http-method"                              -> "other",
    "user-agent"                               -> "user-agent",
    "regkey"                                   -> "registry",
    "regkey|value"                             -> "registry",
    "AS"                                       -> "other",
    "snort"                                    -> "other",
    "pattern-in-file"                          -> "other",
    "pattern-in-traffic"                       -> "other",
    "pattern-in-memory"                        -> "other",
    "yara"                                     -> "other",
    "sigma"                                    -> "other",
    "vulnerability"                            -> "other",
    "attachment"                               -> "file",
    "malware-sample"                           -> "file",
    "link"                                     -> "other",
    "comment"                                  -> "other",
    "text"                                     -> "other",
    "hex"                                      -> "other",
    "other"                                    -> "other",
    "named"                                    -> "other",
    "mutex"                                    -> "other",
    "target-user"                              -> "other",
    "target-email"                             -> "mail",
    "target-machine"                           -> "fqdn",
    "target-org"                               -> "other",
    "target-location"                          -> "other",
    "target-external"                          -> "other",
    "btc"                                      -> "other",
    "iban"                                     -> "other",
    "bic"                                      -> "other",
    "bank-account-nr"                          -> "other",
    "aba-rtn"                                  -> "other",
    "bin"                                      -> "other",
    "cc-number"                                -> "other",
    "prtn"                                     -> "other",
    "threat-actor"                             -> "other",
    "campaign-name"                            -> "other",
    "campaign-id"                              -> "other",
    "malware-type"                             -> "other",
    "uri"                                      -> "uri_path",
    "authentihash"                             -> "other",
    "ssdeep"                                   -> "hash",
    "imphash"                                  -> "hash",
    "pehash"                                   -> "hash",
    "impfuzzy"                                 -> "hash",
    "sha224"                                   -> "hash",
    "sha384"                                   -> "hash",
    "sha512"                                   -> "hash",
    "sha512/224"                               -> "hash",
    "sha512/256"                               -> "hash",
    "tlsh"                                     -> "other",
    "filename|authentihash"                    -> "other",
    "filename|ssdeep"                          -> "other",
    "filename|imphash"                         -> "other",
    "filename|impfuzzy"                        -> "other",
    "filename|pehash"                          -> "other",
    "filename|sha224"                          -> "other",
    "filename|sha384"                          -> "other",
    "filename|sha512"                          -> "other",
    "filename|sha512/224"                      -> "other",
    "filename|sha512/256"                      -> "other",
    "filename|tlsh"                            -> "other",
    "windows-scheduled-task"                   -> "other",
    "windows-service-name"                     -> "other",
    "windows-service-displayname"              -> "other",
    "whois-registrant-email"                   -> "mail",
    "whois-registrant-phone"                   -> "other",
    "whois-registrant-name"                    -> "other",
    "whois-registrar"                          -> "other",
    "whois-creation-date"                      -> "other",
    "x509-fingerprint-sha1"                    -> "other",
    "dns-soa-email"                            -> "other",
    "size-in-bytes"                            -> "other",
    "counter"                                  -> "other",
    "datetime"                                 -> "other",
    "cpe"                                      -> "other",
    "port"                                     -> "other",
    "ip-dst|port"                              -> "other",
    "ip-src|port"                              -> "other",
    "hostname|port"                            -> "other",
    "email-dst-display-name"                   -> "other",
    "email-src-display-name"                   -> "other",
    "email-header"                             -> "other",
    "email-reply-to"                           -> "other",
    "email-x-mailer"                           -> "other",
    "email-mime-boundary"                      -> "other",
    "email-thread-index"                       -> "other",
    "email-message-id"                         -> "other",
    "github-username"                          -> "other",
    "github-repository"                        -> "other",
    "github-organisation"                      -> "other",
    "jabber-id"                                -> "other",
    "twitter-id"                               -> "other",
    "first-name"                               -> "other",
    "middle-name"                              -> "other",
    "last-name"                                -> "other",
    "date-of-birth"                            -> "other",
    "place-of-birth"                           -> "other",
    "gender"                                   -> "other",
    "passport-number"                          -> "other",
    "passport-country"                         -> "other",
    "passport-expiration"                      -> "other",
    "redress-number"                           -> "other",
    "nationality"                              -> "other",
    "visa-number"                              -> "other",
    "issue-date-of-the-visa"                   -> "other",
    "primary-residence"                        -> "other",
    "country-of-residence"                     -> "other",
    "special-service-request"                  -> "other",
    "frequent-flyer-number"                    -> "other",
    "travel-details"                           -> "other",
    "payment-details"                          -> "other",
    "place-port-of-original-embarkation"       -> "other",
    "place-port-of-clearance"                  -> "other",
    "place-port-of-onward-foreign-destination" -> "other",
    "passenger-name-record-locator-number"     -> "other",
    "mobile-application-id"                    -> "other"
  )
}
