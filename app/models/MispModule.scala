package models

import java.io._
import java.nio.file.Path
import java.nio.file.Files

import org.apache.commons.codec.binary.{ Base64, Base64InputStream }
import play.api.libs.json.{ JsObject, JsValue, Json }
import services.MispSrv

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.{ BasicIO, Process, ProcessIO }
import scala.sys.process._

case class MispModule(
  name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    author: String,
    modulePath: Path,
    loaderCommand: String)(implicit val ec: ExecutionContext) extends Analyzer {

  val license = "AGPL-3.0"
  val url = "https://github.com/MISP/misp-modules"

  private def stringStream(string: String): InputStream = {
    new ByteArrayInputStream(string.getBytes)
  }
  def analyze(artifact: Artifact): Future[JsObject] = {
    val input = artifact match {
      case DataArtifact(data, _) ⇒
        stringStream(Json.obj(artifact.dataType → data).toString)
      case FileArtifact(data, _) ⇒
        new SequenceInputStream(Iterator(
          stringStream("""{"file":""""),
          new Base64InputStream(new FileInputStream(data), true),
          stringStream("\"}")).asJavaEnumeration)
    }
    val output = (s"$loaderCommand --run $modulePath" #< input).!!
    Future {
      Json.parse(output).as[JsObject]
    }
  }
}

object MispModule {
  def apply(
    loaderCommand: String,
    modulePath: Path,
    mispSrv: MispSrv)(implicit ec: ExecutionContext): Option[MispModule] = {
    val moduleInfo = Json.parse(s"$loaderCommand --info $modulePath".!!)
    for {
      name ← (moduleInfo \ "name").asOpt[String]
      version ← (moduleInfo \ "moduleinfo" \ "version").asOpt[String]
      description ← (moduleInfo \ "moduleinfo" \ "description").asOpt[String]
      dataTypeList ← (moduleInfo \ "mispattributes" \ "input")
        .asOpt[Seq[String]]
        .map(_.map(mispSrv.mispType2dataType(_)))
      author ← (moduleInfo \ "moduleinfo" \ "author").asOpt[String]
    } yield MispModule(name, version, description, dataTypeList, author, modulePath, loaderCommand)
  }
}

//{'mispattributes': {
//   'input': ['ip-src', 'ip-dst', 'domain|ip'],
//   'output': ['hostname']
// },
// 'moduleinfo': {
//  'version': '0.1',
// 'author': 'Andreas Muehlemann',
// 'description': 'Simple Reverse DNS expansion service to resolve reverse DNS from MISP attributes', 'module-type': ['expansion', 'hover']}}