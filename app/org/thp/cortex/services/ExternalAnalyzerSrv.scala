//package services
//
//import java.io.{ BufferedReader, File, InputStreamReader }
//import java.nio.file.{ Files, Path, Paths }
//import javax.inject.{ Inject, Singleton }
//
//import akka.actor.ActorSystem
//import com.fasterxml.jackson.core.JsonParseException
//import com.fasterxml.jackson.databind.JsonMappingException
//import models.JsonFormat._
//import models._
//import util.JsonConfig
//import play.api.libs.json._
//import play.api.{ Configuration, Logger }
//
//import scala.collection.JavaConversions.iterableAsScalaIterable
//import scala.concurrent.{ ExecutionContext, Future }
//import scala.sys.process.{ BasicIO, Process, ProcessIO }
//import scala.util.{ Failure, Try }
//
//@Singleton
//class ExternalAnalyzerSrv(
//    analyzerPath: Path,
//    disabledAnalyzers: Seq[String],
//    analyzerConfig: JsObject,
//    akkaSystem: ActorSystem) {
//
//  @Inject() def this(configuration: Configuration, akkaSystem: ActorSystem) =
//    this(
//      Paths.get(configuration.getString("analyzer.path").getOrElse(".")),
//      configuration.getStringSeq("analyzer.disabled").getOrElse(Nil),
//      JsonConfig.configWrites.writes(configuration.getConfig("analyzer.config").getOrElse(Configuration.empty)),
//      akkaSystem)
//
//  private[ExternalAnalyzerSrv] lazy val analyzeExecutionContext: ExecutionContext =
//    akkaSystem.dispatchers.lookup("analyzer")
//  private[ExternalAnalyzerSrv] lazy val globalConfig: JsObject =
//    (analyzerConfig \ "global").asOpt[JsObject].getOrElse(JsObject(Nil))
//  private[ExternalAnalyzerSrv] lazy val logger =
//    Logger(getClass)
//
//  lazy val list: Seq[ExternalAnalyzer] = {
//    for {
//      analyzerDir ← Try(Files.newDirectoryStream(analyzerPath).toSeq).getOrElse {
//        logger.warn(s"Analyzer directory ($analyzerPath) is not found")
//        Nil
//      }
//      if Files.isDirectory(analyzerDir)
//      infoFile ← Files.newDirectoryStream(analyzerDir, "*.json").toSeq
//      if Files.isReadable(infoFile)
//      analyzer ← Try(readInfo(infoFile).as[ExternalAnalyzer](reads))
//        .recoverWith {
//          case error ⇒
//            logger.warn(s"Load of analyzer $infoFile fails", error)
//            Failure(error)
//        }
//        .toOption
//        .flatMap {
//          case a if disabledAnalyzers.contains(a.id) ⇒
//            logger.info(s"Analyzer ${a.name} ${a.version} (${a.id}) is disabled")
//            None
//          case a ⇒
//            logger.info(s"Register analyzer ${a.name} ${a.version} (${a.id})")
//            Some(a)
//        }
//    } yield analyzer
//  }
//
//  def get(analyzerId: String): Option[ExternalAnalyzer] = list.find(_.id == analyzerId)
//
//  private val osexec =
//    if (System.getProperty("os.name").toLowerCase.contains("win"))
//      (c: String) ⇒ s"""cmd /c $c"""
//    else
//      (c: String) ⇒ s"""sh -c "./$c" """
//
//  def analyze(analyzer: ExternalAnalyzer, artifact: Artifact): Future[Report] = {
//    Future {
//      val input = artifact match {
//        case FileArtifact(file, attributes) ⇒ attributes + ("file" → JsString(file.getAbsoluteFile.toString)) + ("config" → analyzer.config)
//        case DataArtifact(data, attributes) ⇒ attributes + ("data" → JsString(data)) + ("config" → analyzer.config)
//      }
//      val output = new StringBuffer
//      val error = new StringBuffer
//      try {
//        logger.info(s"Execute ${osexec(analyzer.command.getFileName.toString)} in ${analyzer.command.getParent.toFile.getAbsoluteFile.getName}")
//        Process(osexec(analyzer.command.getFileName.toString), analyzer.command.getParent.toFile).run(
//          new ProcessIO(
//            { stdin ⇒
//              try stdin.write(input.toString.getBytes("UTF-8"))
//              finally stdin.close()
//            }, { stdout ⇒
//              val reader = new BufferedReader(new InputStreamReader(stdout, "UTF-8"))
//              try BasicIO.processLinesFully { line ⇒
//                output.append(line).append(System.lineSeparator())
//                ()
//              }(reader.readLine _)
//              finally reader.close()
//            }, { stderr ⇒
//              val reader = new BufferedReader(new InputStreamReader(stderr, "UTF-8"))
//              try BasicIO.processLinesFully { line ⇒
//                error.append(line).append(System.lineSeparator())
//                ()
//              }(reader.readLine _)
//              finally reader.close()
//            })).exitValue
//        Json.parse(output.toString).as[Report]
//      }
//      catch {
//        case _: JsonMappingException ⇒
//          error.append(output)
//          FailureReport(s"Error: Invalid output\n$error", JsNull)
//        case _: JsonParseException ⇒
//          error.append(output)
//          FailureReport(s"Error: Invalid output\n$error", JsNull)
//        case t: Throwable ⇒
//          FailureReport(t.getMessage + ":" + t.getStackTrace.mkString("", "\n\t", "\n"), JsNull)
//      }
//    }(analyzeExecutionContext)
//  }
//
//  private[ExternalAnalyzerSrv] def readInfo(file: Path): JsValue = {
//    val source = scala.io.Source.fromFile(file.toFile)
//    try Json.parse(source.mkString)
//    finally source.close()
//  }
//
//  private[ExternalAnalyzerSrv] val reads: Reads[ExternalAnalyzer] =
//    for {
//      name ← (__ \ "name").read[String]
//      version ← (__ \ "version").read[String]
//      description ← (__ \ "description").read[String]
//      dataTypeList ← (__ \ "dataTypeList").read[Seq[String]]
//      author ← (__ \ "author").read[String]
//      url ← (__ \ "url").read[String]
//      license ← (__ \ "license").read[String]
//      command ← (__ \ "command").read[String]
//      absoluteCommand = analyzerPath.resolve(Paths.get(command.replaceAll("[\\/]", File.separator)))
//      config ← (__ \ "config").read[JsObject]
//      baseConfigKey ← (__ \ "baseConfig").readNullable[String]
//      baseConfig = baseConfigKey
//        .flatMap(bc ⇒ (analyzerConfig \ bc).asOpt[JsObject])
//        .getOrElse(Json.obj())
//    } yield ExternalAnalyzer(
//      name,
//      version,
//      description,
//      dataTypeList,
//      author,
//      url,
//      license,
//      absoluteCommand, globalConfig deepMerge baseConfig deepMerge config)
//}