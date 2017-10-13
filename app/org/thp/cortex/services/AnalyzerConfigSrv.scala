package org.thp.cortex.services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import org.thp.cortex.models.{ AnalyzerConfig, AnalyzerConfigModel }

import org.elastic4play.NotFoundError
import org.elastic4play.services.{ CreateSrv, FindSrv, GetSrv, QueryDef }

@Singleton
class AnalyzerConfigSrv @Inject() (
    analyzerConfigModel: AnalyzerConfigModel,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    findSrv: FindSrv,
    userSrv: UserSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  def get(analyzerConfigId: String): Future[AnalyzerConfig] = getSrv[AnalyzerConfigModel, AnalyzerConfig](analyzerConfigModel, analyzerConfigId)

  def get(userId: String, analyzerId: String): Future[AnalyzerConfig] = {
    import org.elastic4play.services.QueryDSL._
    for {
      user ← userSrv.get(userId)
      subscriptionId = user.subscription()
      analyzerConfig ← find(
        and(withParent("subscription", subscriptionId), "analyzerId" ~= analyzerId),
        Some("0-1"), Nil)._1.runWith(Sink.headOption)
    } yield analyzerConfig.getOrElse(throw NotFoundError(s"Configuration for analyzer $analyzerId not found for subscription $subscriptionId ($userId)"))
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[AnalyzerConfig, NotUsed], Future[Long]) = {
    findSrv[AnalyzerConfigModel, AnalyzerConfig](analyzerConfigModel, queryDef, range, sortBy)
  }

}
