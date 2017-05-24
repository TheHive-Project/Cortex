package services

import java.io.{ ByteArrayInputStream, FileInputStream, InputStream, SequenceInputStream }
import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import models.JsonFormat._
import models._
import org.apache.commons.codec.binary.{ Base64, Base64InputStream }
import util.JsonConfig
import play.api.libs.json.{ Json, _ }
import play.api.{ Configuration, Logger }

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.sys.process._
import scala.util.{ Failure, Success, Try }

@Singleton
class MispSrv(
    mispModulesEnabled: Boolean,
    loaderCommand: String,
    mispModuleConfig: JsObject,
    externalAnalyzerSrv: ExternalAnalyzerSrv,
    jobSrv: JobSrv,
    akkaSystem: ActorSystem) {

  @Inject() def this(
    configuration: Configuration,
    externalAnalyzerSrv: ExternalAnalyzerSrv,
    jobSrv: JobSrv,
    akkaSystem: ActorSystem) = this(
    configuration.getBoolean("misp.modules.enabled").getOrElse(false),
    configuration.getString("misp.modules.loader").get,
    JsonConfig.configWrites.writes(configuration.getConfig("misp.modules.config").getOrElse(Configuration.empty)),
    externalAnalyzerSrv,
    jobSrv,
    akkaSystem)

  private[MispSrv] lazy val logger = Logger(getClass)
  private[MispSrv] lazy val analyzeExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("analyzer")

  logger.info(s"MISP modules is ${if (mispModulesEnabled) "enabled" else "disabled"}, loader is $loaderCommand")

  private[MispSrv] val futureList: Future[Seq[MispModule]] = Future {
    if (mispModulesEnabled) {
      val moduleNameList = Try(Json.parse(s"$loaderCommand --list".!!).as[Seq[String]]) match {
        case Success(l) ⇒ l
        case Failure(error) ⇒
          logger.error(s"MISP module loader fails", error)
          Nil
      }

      moduleNameList
        .map { moduleName ⇒
          moduleName → (for {
            moduleInfo ← Try(Json.parse(s"$loaderCommand --info $moduleName".!!))
            module ← Try(moduleInfo.as[MispModule](reads(loaderCommand, mispModuleConfig)))
          } yield module)
        }
        .flatMap {
          case (moduleName, Failure(error)) ⇒
            logger.warn(s"Load MISP module $moduleName fails: ${error.getMessage}")
            Nil
          case (_, Success(module)) ⇒
            logger.info(s"Register MISP module ${module.name} ${module.version}")
            Seq(module)
        }
    }
    else Nil
  }(analyzeExecutionContext)
  lazy val list: Seq[MispModule] = Await.result(futureList, 5.minutes)

  def get(moduleName: String): Option[MispModule] = list.find(_.name == moduleName)

  def moduleList: JsArray = {
    val mispModules = list.map { module ⇒
      Json.obj(
        "name" → module.name,
        "type" → "cortex",
        "mispattributes" → Json.obj(
          "input" → module.inputAttributes,
          "output" → Json.arr()),
        "meta" → Json.obj(
          "module-type" → Json.arr("cortex"),
          "description" → module.description,
          "author" → module.author,
          "version" → module.version,
          "config" → module.config))
    }
    val externalAnalyzers = externalAnalyzerSrv.list.map { analyzer ⇒
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
    }
    JsArray(mispModules ++ externalAnalyzers)
  }

  def query(module: String, mispType: String, data: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    val artifact = toArtifact(mispType, data)
    val mispModule = if (mispModulesEnabled) {
      get(module)
        .map { mispModule ⇒
          val mispReport = Future {
            val input = Json.obj("config" → mispModule.config, mispType → data)
            val output = (s"$loaderCommand --run $module" #< input.toString).!!
            Json.parse(output).as[JsObject]
          }
          jobSrv.create(mispModule, artifact, mispReport.map(toReport))
          mispReport

        }
    }
    else None
    mispModule
      .orElse {
        externalAnalyzerSrv
          .get(module)
          .map { analyzer ⇒
            externalAnalyzerSrv.analyze(analyzer, artifact)
              .map { report ⇒ toMispOutput(report) }
          }
      }
      .getOrElse(Future.failed(new Exception(s"Module $module not found"))) // TODO add appropriate exception
  }

  def analyze(module: MispModule, artifact: Artifact): Future[Report] = {
    def stringStream(string: String): InputStream =
      new ByteArrayInputStream(string.getBytes)

    val input = artifact match {
      case DataArtifact(data, _) ⇒
        val mispType = dataType2mispType(artifact.dataType)
          .filter(module.inputAttributes.contains)
          .head
        stringStream((Json.obj("config" → module.config) + (mispType → JsString(data))).toString)
      case FileArtifact(data, _) ⇒
        new SequenceInputStream(Iterator(
          stringStream(Json.obj("config" → module.config).toString.replaceFirst("}$", ""","attachment":"""")),
          new Base64InputStream(new FileInputStream(data), true),
          stringStream("\"}")).asJavaEnumeration)
    }

    Future {
      val output = (s"${module.loaderCommand} --run ${module.name}" #< input).!!
      toReport(Json.parse(output).as[JsObject])
    }(analyzeExecutionContext)
  }

  private def toArtifact(mispType: String, data: String): Artifact = {
    mispType2dataType(mispType) match {
      case "file" if mispType == "malware-sample" ⇒ ??? // TODO
      case "file" ⇒ FileArtifact(Base64.decodeBase64(data), Json.obj(
        "tlp" → 1,
        "dataType" → "file"))
      case dataType ⇒ DataArtifact(data, Json.obj(
        "tlp" → 1,
        "dataType" → dataType))
    }
  }

  private def toReport(mispOutput: JsObject): Report = {
    (mispOutput \ "results").asOpt[Seq[JsObject]]
      .map { attributes ⇒
        val artifacts: Seq[Artifact] = for {
          attribute ← attributes
          tpe ← (attribute \ "types").asOpt[Seq[String]]
            .orElse((attribute \ "types").asOpt[String].map(Seq(_)))
            .getOrElse(Nil)
          dataType = mispType2dataType(tpe) // TODO handle FileArtifact
          value ← (attribute \ "values").asOpt[Seq[String]]
            .orElse((attribute \ "values").asOpt[String].map(Seq(_)))
            .getOrElse(Nil)
        } yield DataArtifact(value, Json.obj("dataType" → dataType))
        SuccessReport(artifacts, Json.obj("artifacts" → Json.toJson(artifacts)), JsObject(Nil))
      }
      .getOrElse {
        val message = (mispOutput \ "error").asOpt[String].getOrElse(mispOutput.toString)
        FailureReport(message)
      }
  }

  private def toMispOutput(report: Report): JsObject = {
    report match {
      case SuccessReport(artifacts, _, _) ⇒
        val attributes = artifacts.map {
          case artifact: DataArtifact ⇒
            Json.obj(
              "types" → dataType2mispType(artifact.dataType),
              "values" → Json.arr(artifact.data))
          case artifact: FileArtifact ⇒ ??? // TODO
        }
        val cortexAttribute = Json.obj(
          "types" → Seq("cortex"),
          "values" → Json.arr(Json.toJson(report).toString))

        Json.obj("results" → (attributes :+ cortexAttribute))
      case FailureReport(message) ⇒
        Json.obj("error" → message)
    }
  }

  private def mispType2dataType(mispType: String): String = typeLookup.getOrElse(mispType, {
    logger.warn(s"Misp type $mispType not recognized")
    "other"
  })

  private def dataType2mispType(dataType: String): Seq[String] = {
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

  private def reads(loaderCommand: String, mispModuleConfig: JsObject): Reads[MispModule] =
    for {
      name ← (__ \ "name").read[String]
      version ← (__ \ "moduleinfo" \ "version").read[String]
      description ← (__ \ "moduleinfo" \ "description").read[String]
      author ← (__ \ "moduleinfo" \ "author").read[String]
      config = (mispModuleConfig \ name).asOpt[JsObject].getOrElse(JsObject(Nil))
      requiredConfig ← (__ \ "config").read[Set[String]]
      missingConfig = requiredConfig -- config.keys
      _ ← if (missingConfig.nonEmpty) {
        Reads[Unit](_ ⇒ JsError(s"MISP module $name is disabled because the following configuration " +
          s"item${if (missingConfig.size > 1) "s are" else " is"} missing: ${missingConfig.mkString(", ")}"))
      }
      else {
        Reads[Unit](_ ⇒ JsSuccess(()))
      }
      input ← (__ \ "mispattributes" \ "input").read[Seq[String]]
      dataTypes = input.map(mispType2dataType).distinct
    } yield MispModule(name, version, description, author, dataTypes, input, config, loaderCommand)

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
    "target-machine" → "fqdn",
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