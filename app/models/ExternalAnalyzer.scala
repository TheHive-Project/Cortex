package models

import java.io.{ BufferedReader, InputStreamReader }
import java.nio.file.Path

import scala.concurrent.{ ExecutionContext, Future, blocking }
import scala.sys.process.{ BasicIO, Process, ProcessIO }

import akka.stream.Materializer

import play.api.Logger
import play.api.libs.json.{ JsObject, JsString, Json }

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException

case class ExternalAnalyzer(
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    author: String,
    url: String,
    license: String,
    command: Path,
    config: JsObject)(implicit val ec: ExecutionContext) extends Analyzer {

  val log = Logger(getClass)
  private val osexec = if (System.getProperty("os.name").toLowerCase.contains("win"))
    (c: String) ⇒ s"""cmd /c $c"""
  else
    (c: String) ⇒ s"""sh -c "./$c" """

  override def analyze(artifact: Artifact): Future[JsObject] = {
    Future {
      val input = artifact match {
        case FileArtifact(file, attributes) ⇒ attributes + ("file" → JsString(file.getAbsoluteFile.toString)) + ("config" → config)
        case DataArtifact(data, attributes) ⇒ attributes + ("data" → JsString(data)) + ("config" → config)
      }
      val output = new StringBuffer
      val error = new StringBuffer
      try {
        log.info(s"Execute ${osexec(command.getFileName.toString)} in ${command.getParent.toFile.getAbsoluteFile.getName}")
        val exitValue = Process(osexec(command.getFileName.toString), command.getParent.toFile).run(
          new ProcessIO(
            { stdin ⇒
              try stdin.write(input.toString.getBytes("UTF-8"))
              finally stdin.close()
            },
            { stdout ⇒
              val reader = new BufferedReader(new InputStreamReader(stdout, "UTF-8"))
              try BasicIO.processLinesFully { line ⇒
                output.append(line).append(System.lineSeparator())
                ()
              }(reader.readLine)
              finally reader.close()
            },
            { stderr ⇒
              val reader = new BufferedReader(new InputStreamReader(stderr, "UTF-8"))
              try BasicIO.processLinesFully { line ⇒
                error.append(line).append(System.lineSeparator())
                ()
              }(reader.readLine)
              finally reader.close()
            })).exitValue
        Json.parse(output.toString).as[JsObject]
      }
      catch {
        case _: JsonMappingException ⇒
          error.append(output)
          JsObject(Seq("errorMessage" → JsString(s"Error: Invalid output\n$error")))
        case _: JsonParseException ⇒
          error.append(output)
          JsObject(Seq("errorMessage" → JsString(s"Error: Invalid output\n$error")))
        case t: Throwable ⇒
          JsObject(Seq("errorMessage" → JsString(t.getMessage + ":" + t.getStackTrace().mkString("", "\n\t", "\n"))))
      }
    }
  }
}
