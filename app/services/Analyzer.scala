package services

import java.io.File
import java.nio.file.{ Files, Path, Paths }
import javax.inject.Inject

import akka.actor.ActorSystem
import models.{ Analyzer, ExternalAnalyzer, MispModule }
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.{ Configuration, Logger }
import util.JsonConfig.configWrites

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.ExecutionContext
import scala.util.Try

class AnalyzerSrv(
    mispSrv: MispSrv,
    analyzerPath: Path,
    analyzerConfig: JsObject,
    mispModulesPath: Path,
    mispModuleLoaderCommand: Option[String],
    akkaSystem: ActorSystem) {
  @Inject def this(
    mispSrv: MispSrv,
    configuration: Configuration,
    akkaSystem: ActorSystem) =
    this(
      mispSrv,
      Paths.get(configuration.getString("analyzer.path").getOrElse(".")),
      configWrites.writes(configuration.getConfig("analyzer.config").getOrElse(Configuration.empty)),
      Paths.get(configuration.getString("misp.modules.path").getOrElse(".")),
      configuration.getString("misp.modules.loader"),
      akkaSystem)

  lazy val log = Logger(getClass)
  lazy val analyzeExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("analyzer")

  private lazy val externalAnalyzers: Seq[Analyzer] = getExternalAnalyzers
  def list: Seq[Analyzer] = externalAnalyzers // ::: javaAnalyzers
  def get(analyzerId: String): Option[Analyzer] = list.find(_.id == analyzerId)
  def listForType(dataType: String): Seq[Analyzer] = list.filter(_.dataTypeList.contains(dataType))

  private[services] def getExternalAnalyzers: Seq[Analyzer] = {
    val globalConfig = (analyzerConfig \ "global").asOpt[JsObject].getOrElse(JsObject(Nil))
    for {
      analyzerDir ← Try(Files.newDirectoryStream(analyzerPath).toSeq).getOrElse {
        log.warn(s"Analyzer directory ($analyzerPath) is not found")
        Nil
      }
      if Files.isDirectory(analyzerDir)
      infoFile ← Files.newDirectoryStream(analyzerDir, "*.json").toSeq
      if Files.isReadable(infoFile)
      info = readInfo(infoFile)
      name ← (info \ "name").asOpt[String] orElse {
        log.warn(s"name is missing in $infoFile"); None
      }
      version ← (info \ "version").asOpt[String] orElse {
        log.warn(s"version is missing in $infoFile"); None
      }
      description ← (info \ "description").asOpt[String] orElse {
        log.warn(s"description is missing in $infoFile"); None
      }
      dataTypeList ← (info \ "dataTypeList").asOpt[Seq[String]] orElse {
        log.warn(s"dataTypeList is missing in $infoFile"); None
      }
      command ← (info \ "command").asOpt[String] orElse {
        log.warn(s"command is missing in $infoFile"); None
      }
      author ← (info \ "author").asOpt[String] orElse {
        log.warn(s"author is missing in $infoFile"); None
      }
      url ← (info \ "url").asOpt[String] orElse {
        log.warn(s"url is missing in $infoFile"); None
      }
      license ← (info \ "license").asOpt[String] orElse {
        log.warn(s"license is missing in $infoFile"); None
      }
      config = (info \ "config").asOpt[JsObject].getOrElse(JsObject(Nil))
      baseConfig = (info \ "baseConfig").asOpt[String].flatMap(c ⇒ (analyzerConfig \ c).asOpt[JsObject]).getOrElse(JsObject(Nil))
      absoluteCommand = analyzerPath.resolve(Paths.get(command.replaceAll("[\\/]", File.separator)))
      _ = log.info(s"Register analyzer $name $version (${(name + "_" + version).replaceAll("\\.", "_")})")
    } yield ExternalAnalyzer(name, version, description, dataTypeList, author, url, license, absoluteCommand, globalConfig deepMerge baseConfig deepMerge config)(analyzeExecutionContext)
  }

  private[services] def getMispModules: Seq[Analyzer] = {
    for {
      moduleFile ← Try(Files.newDirectoryStream(mispModulesPath).toSeq).getOrElse {
        log.warn(s"MISP modules directory ($mispModulesPath) is not found")
        Nil
      }
      loaderCommand ← mispModuleLoaderCommand
      if Files.isRegularFile(moduleFile) && moduleFile.toString.endsWith(".py")
      mispModule ← MispModule(loaderCommand, moduleFile, mispSrv)(analyzeExecutionContext)
    } yield mispModule
  }

  private[services] def readInfo(file: Path): JsValue = {
    val source = scala.io.Source.fromFile(file.toFile)
    try {
      Json.parse(source.mkString)
    }
    finally { source.close() }
  }
}
