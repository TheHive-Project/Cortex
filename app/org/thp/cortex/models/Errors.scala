package org.thp.cortex.models

abstract class CortexError(message: String) extends Exception(message)

case class JobNotFoundError(jobId: String) extends CortexError(s"Job $jobId not found")
case class AnalyzerNotFoundError(analyzerId: String) extends CortexError(s"Analyzer $analyzerId not found")