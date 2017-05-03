package services

import java.io.File
import java.nio.file.{ Files, Path, Paths }
import javax.inject.{ Inject, Provider, Singleton }

import akka.actor.ActorSystem
import models.{ Analyzer, ExternalAnalyzer, MispModule }
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.{ Configuration, Logger }
import util.JsonConfig.configWrites

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.ExecutionContext
import scala.util.Try

@Singleton
class AnalyzerSrv(
    mispSrvProvider: Provider[MispSrv],
    analyzerPath: Path,
    analyzerConfig: JsObject,
    mispModulesPath: Path,
    mispModuleLoaderCommand: Option[String],
    akkaSystem: ActorSystem) {
  @Inject def this(
    mispSrvProvider: Provider[MispSrv],
    configuration: Configuration,
    akkaSystem: ActorSystem) =
    this(
      mispSrvProvider,
      Paths.get(configuration.getString("analyzer.path").getOrElse(".")),
      configWrites.writes(configuration.getConfig("analyzer.config").getOrElse(Configuration.empty)),
      Paths.get(configuration.getString("misp.modules.path").getOrElse(".")),
      configuration.getString("misp.modules.loader"),
      akkaSystem)

  private[AnalyzerSrv] lazy val logger = Logger(getClass)
  lazy val analyzeExecutionContext: ExecutionContext = akkaSystem.dispatchers.lookup("analyzer")
  lazy val mispSrv = mispSrvProvider.get

  private lazy val externalAnalyzers: Seq[Analyzer] = getExternalAnalyzers
  private lazy val mispModules: Seq[Analyzer] = getMispModules
  def list: Seq[Analyzer] = externalAnalyzers ++ mispModules // ::: javaAnalyzers
  def get(analyzerId: String): Option[Analyzer] = list.find(_.id == analyzerId)
  def listForType(dataType: String): Seq[Analyzer] = list.filter(_.dataTypeList.contains(dataType))

  private[services] def getExternalAnalyzers: Seq[Analyzer] = {
    val globalConfig = (analyzerConfig \ "global").asOpt[JsObject].getOrElse(JsObject(Nil))
    for {
      analyzerDir ← Try(Files.newDirectoryStream(analyzerPath).toSeq).getOrElse {
        logger.warn(s"Analyzer directory ($analyzerPath) is not found")
        Nil
      }
      if Files.isDirectory(analyzerDir)
      infoFile ← Files.newDirectoryStream(analyzerDir, "*.json").toSeq
      if Files.isReadable(infoFile)
      info = readInfo(infoFile)
      name ← (info \ "name").asOpt[String] orElse {
        logger.warn(s"name is missing in $infoFile"); None
      }
      version ← (info \ "version").asOpt[String] orElse {
        logger.warn(s"version is missing in $infoFile"); None
      }
      description ← (info \ "description").asOpt[String] orElse {
        logger.warn(s"description is missing in $infoFile"); None
      }
      dataTypeList ← (info \ "dataTypeList").asOpt[Seq[String]] orElse {
        logger.warn(s"dataTypeList is missing in $infoFile"); None
      }
      command ← (info \ "command").asOpt[String] orElse {
        logger.warn(s"command is missing in $infoFile"); None
      }
      author ← (info \ "author").asOpt[String] orElse {
        logger.warn(s"author is missing in $infoFile"); None
      }
      url ← (info \ "url").asOpt[String] orElse {
        logger.warn(s"url is missing in $infoFile"); None
      }
      license ← (info \ "license").asOpt[String] orElse {
        logger.warn(s"license is missing in $infoFile"); None
      }
      config = (info \ "config").asOpt[JsObject].getOrElse(JsObject(Nil))
      baseConfig = (info \ "baseConfig").asOpt[String].flatMap(c ⇒ (analyzerConfig \ c).asOpt[JsObject]).getOrElse(JsObject(Nil))
      absoluteCommand = analyzerPath.resolve(Paths.get(command.replaceAll("[\\/]", File.separator)))
      _ = logger.info(s"Register analyzer $name $version (${(name + "_" + version).replaceAll("\\.", "_")})")
    } yield ExternalAnalyzer(name, version, description, dataTypeList, author, url, license, absoluteCommand, globalConfig deepMerge baseConfig deepMerge config)(analyzeExecutionContext)
  }

  private[services] def getMispModules: Seq[Analyzer] = {
    for {
      loaderCommand ← mispModuleLoaderCommand.toSeq
      moduleName ← MispModule.list(loaderCommand)

      _ = println("MISP module loading ...")
      mispModule ← MispModule(loaderCommand, moduleName, mispSrv)(analyzeExecutionContext)
      _ = println("MISP module load success")
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
