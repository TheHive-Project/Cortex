package models

import java.io._

import org.apache.commons.codec.binary.Base64InputStream
import play.api.Logger
import play.api.libs.json.{ JsObject, Json }
import services.MispSrv

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process._
import scala.util.{ Failure, Success, Try }

case class MispModule(
    mispSrv: MispSrv,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    author: String,
    moduleName: String,
    loaderCommand: String)(implicit val ec: ExecutionContext) extends Analyzer {

  val license = "AGPL-3.0"
  val url = "https://github.com/MISP/misp-modules"

  private def stringStream(string: String): InputStream = {
    new ByteArrayInputStream(string.getBytes)
  }
  def analyze(artifact: Artifact): Future[JsObject] = {
    val input = artifact match {
      case DataArtifact(data, _) ⇒
        stringStream(Json.obj(mispSrv.dataType2mispType(artifact.dataType).head → data).toString)
      case FileArtifact(data, _) ⇒
        new SequenceInputStream(Iterator(
          stringStream("""{"attachment":""""),
          new Base64InputStream(new FileInputStream(data), true),
          stringStream("\"}")).asJavaEnumeration)
    }
    val output = (s"$loaderCommand --run $moduleName" #< input).!!
    Future {
      Json.parse(output).as[JsObject]
    }
  }
}

object MispModule {
  private[MispModule] lazy val logger = Logger(getClass)
  def list(loaderCommand: String): Seq[String] =
    Json.parse(s"$loaderCommand --list".!!).as[Seq[String]]

  def apply(
    loaderCommand: String,
    moduleName: String,
    mispSrv: MispSrv)(implicit ec: ExecutionContext): Option[MispModule] = {
    println(s"Loading MISP module $moduleName")
    for {
      moduleInfo ← Try(Json.parse(s"$loaderCommand --info $moduleName".!!)) match {
        case Success(s) ⇒ Some(s)
        case Failure(f) ⇒
          f.printStackTrace()
          None
      }
      name ← (moduleInfo \ "name").asOpt[String].orElse {
        println("name not defined")
        None
      }
      version ← (moduleInfo \ "moduleinfo" \ "version").asOpt[String].orElse {
        println("version not defined")
        None
      }
      description ← (moduleInfo \ "moduleinfo" \ "description").asOpt[String].orElse {
        println("description not defined")
        None
      }
      dataTypeList ← (moduleInfo \ "mispattributes" \ "input")
        .asOpt[Seq[String]]
        .map(_.map(mispSrv.mispType2dataType(_)).distinct)
        .orElse {
          println("input attributes not defined")
          None
        }
      author ← (moduleInfo \ "moduleinfo" \ "author").asOpt[String].orElse {
        println("author not defined")
        None
      }
      mispModule ← Try(MispModule(mispSrv, name, version, description, dataTypeList, author, moduleName, loaderCommand)) match {
        case Success(s) ⇒ Some(s)
        case Failure(f) ⇒
          f.printStackTrace()
          sys.error("Load module fails")
      }
      _ = println("Module load succeed")
    } yield mispModule
  }
}