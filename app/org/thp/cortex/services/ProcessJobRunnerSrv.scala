package org.thp.cortex.services

import java.nio.file.{ Path, Paths }

import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.{ Process, ProcessLogger }

import play.api.Logger

import javax.inject.Singleton
import org.thp.cortex.models._

@Singleton
class ProcessJobRunnerSrv {

  lazy val logger = Logger(getClass)

  def run(jobDirectory: Path, command: String, job: Job)(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      val baseDirectory = Paths.get(command).getParent.getParent
      logger.info(s"Execute $command in $baseDirectory")
      Process(Seq(command, jobDirectory.toString), baseDirectory.toFile)
        .run(ProcessLogger(s ⇒ logger.info(s"  Job ${job.id}: $s"))).exitValue()
      ()
    }
  }
}
