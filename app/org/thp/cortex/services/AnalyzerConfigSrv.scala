package org.thp.cortex.services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import org.thp.cortex.models.{ AnalyzerConfig, AnalyzerConfigModel }

import org.elastic4play.NotFoundError
import org.elastic4play.services._

@Singleton
class AnalyzerConfigSrv @Inject()(
                                   analyzerConfigModel: AnalyzerConfigModel,
                                   getSrv: GetSrv,
                                   createSrv: CreateSrv,
                                   findSrv: FindSrv,
                                   userSrv: UserSrv,
                                   implicit val ec: ExecutionContext,
                                   implicit val mat: Materializer) {

  def get(analyzerConfigId: String): Future[AnalyzerConfig] = getSrv[AnalyzerConfigModel, AnalyzerConfig](analyzerConfigModel, analyzerConfigId)

  def getForUser(userId: String, analyzerId: String): Future[AnalyzerConfig] = {
    userSrv.get(userId)
      .flatMap(user => getForSubscription(user.subscription(), analyzerId))
  }

  def getForSubscription(subscriptionId: String, analyzerId: String): Future[AnalyzerConfig] = {
    import org.elastic4play.services.QueryDSL._
    find(
      and(withParent("subscription", subscriptionId), "analyzerId" ~= analyzerId),
      Some("0-1"), Nil)._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Configuration for analyzer $analyzerId not found for subscription $subscriptionId")))
  }

  def findForUser(userId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[AnalyzerConfig, NotUsed], Future[Long]) = {
    val analyzerConfigs = for {
      user <- userSrv.get(userId)
      subscriptionId = user.subscription()
      analyserConfigs = findForSubscription(subscriptionId, queryDef, range, sortBy)
    } yield analyserConfigs
    val analyserConfigSource = Source.fromFutureSource(analyzerConfigs.map(_._1)).mapMaterializedValue(_ => NotUsed)
    val analyserConfigTotal = analyzerConfigs.flatMap(_._2)
    analyserConfigSource -> analyserConfigTotal
  }

  def findForSubscription(subscriptionId: String, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]) = {
    import org.elastic4play.services.QueryDSL._
    find(and(withParent("subscription", subscriptionId), queryDef), range, sortBy)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[AnalyzerConfig, NotUsed], Future[Long]) = {
    findSrv[AnalyzerConfigModel, AnalyzerConfig](analyzerConfigModel, queryDef, range, sortBy)
  }
}
