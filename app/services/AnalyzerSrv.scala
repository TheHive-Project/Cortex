package services

import javax.inject.{ Inject, Singleton }

import models._
import play.api.Logger

import scala.concurrent.Future

@Singleton
class AnalyzerSrv @Inject() (
    jobSrv: JobSrv,
    mispSrv: MispSrv,
    externalAnalyzerSrv: ExternalAnalyzerSrv) {

  private[AnalyzerSrv] lazy val logger = Logger(getClass)

  def list: Seq[Analyzer] = externalAnalyzerSrv.list ++ mispSrv.list

  def get(analyzerId: String): Option[Analyzer] = list.find(_.id == analyzerId)

  def listForType(dataType: String): Seq[Analyzer] = list.filter(_.dataTypeList.contains(dataType))

  def analyze(analyzerId: String, artifact: Artifact): Future[Job] = {
    get(analyzerId)
      .map { analyzer ⇒ analyze(analyzer, artifact) }
      .getOrElse(throw AnalyzerNotFoundError(analyzerId))
  }

  def analyze(analyzer: Analyzer, artifact: Artifact): Future[Job] = {
    val report = analyzer match {
      case ea: ExternalAnalyzer ⇒ externalAnalyzerSrv.analyze(ea, artifact)
      case mm: MispModule       ⇒ mispSrv.analyze(mm, artifact)
    }
    jobSrv.create(analyzer, artifact, report)
  }
}