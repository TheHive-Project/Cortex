package org.thp.cortex.models

abstract class CortexError(message: String) extends Exception(message)

case class JobNotFoundError(jobId: String)         extends CortexError(s"Job $jobId not found")
case class WorkerNotFoundError(analyzerId: String) extends CortexError(s"Worker $analyzerId not found")
case class UnknownConfigurationItem(item: String)  extends CortexError(s"Configuration item $item is not known")
case class RateLimitExceeded(analyzer: Worker)
    extends CortexError(
      s"Rate limit of ${analyzer.rate().getOrElse("(not set ?!)")} per ${analyzer.rateUnit().getOrElse("(not set ?!)")} reached for the analyzer ${analyzer.name()}. Job cannot be started"
    )
