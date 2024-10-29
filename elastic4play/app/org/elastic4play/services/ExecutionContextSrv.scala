package org.elastic4play.services

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.cache.SyncCacheApi

import scala.concurrent.ExecutionContext
import scala.util.Try

@Singleton
class ExecutionContextSrv @Inject() (system: ActorSystem, syncCacheApi: SyncCacheApi) {
  lazy val logger: Logger       = Logger(getClass)
  val default: ExecutionContext = system.dispatcher

  def get(threadPoolName: String): ExecutionContext =
    syncCacheApi.getOrElseUpdate(s"threadPool-$threadPoolName") {
      Try(system.dispatchers.lookup(threadPoolName)).getOrElse {
        logger.warn(s"""The configuration of thread pool $threadPoolName is not found. Fallback to default thread pool.
                       |In order to use a dedicated thread pool, add the following configuration in application.conf:
                       |    $threadPoolName {
                       |      fork-join-executor {
                       |        # Number of threads = min(parallelism-max, max(parallelism-min, ceil(available processors * parallelism-factor)))
                       |        parallelism-min = 1
                       |        parallelism-factor = 2.0
                       |        parallelism-max = 4
                       |    }
                       |  }
                       |""".stripMargin)
        default
      }
    }
  def withCustom[A](threadPoolName: String)(body: ExecutionContext => A): A = body(get(threadPoolName))
  def withDefault[A](body: ExecutionContext => A): A                        = body(default)
}
