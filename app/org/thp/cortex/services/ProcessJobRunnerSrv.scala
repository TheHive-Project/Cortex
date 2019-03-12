package org.thp.cortex.services

import java.nio.file.{ Path, Paths }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.{ Process, ProcessLogger }

import play.api.Logger

import akka.actor.ActorSystem
import javax.inject.{ Inject, Singleton }
import org.thp.cortex.models._

import org.elastic4play.utils.RichFuture

@Singleton
class ProcessJobRunnerSrv @Inject() (implicit val system: ActorSystem) {

  lazy val logger = Logger(getClass)

  def run(jobDirectory: Path, command: String, job: Job, timeout: Option[FiniteDuration])(implicit ec: ExecutionContext): Future[Unit] = {
    val baseDirectory = Paths.get(command).getParent.getParent
    logger.info(s"Execute $command in $baseDirectory, timeout is ${timeout.fold("none")(_.toString)}")
    val process = Process(Seq(command, jobDirectory.toString), baseDirectory.toFile)
      .run(ProcessLogger(s ⇒ logger.info(s"  Job ${job.id}: $s")))
    val execution = Future {
      process.exitValue()
      ()
    }
    timeout.fold(execution)(t ⇒ execution.withTimeout(t, killProcess(process)))
  }

  def killProcess(process: Process): Unit = {
    logger.info("Timeout reached, killing process")
    process.destroy()
  }
}
