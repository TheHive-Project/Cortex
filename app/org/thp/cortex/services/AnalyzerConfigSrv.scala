package org.thp.cortex.services

import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.cortex.models.{BaseConfig, WorkerConfigModel, WorkerType}

import org.elastic4play.services.{CreateSrv, FindSrv, UpdateSrv}

@Singleton
class AnalyzerConfigSrv @Inject() (
    val configuration: Configuration,
    val workerConfigModel: WorkerConfigModel,
    val userSrv: UserSrv,
    val organizationSrv: OrganizationSrv,
    val workerSrv: WorkerSrv,
    val createSrv: CreateSrv,
    val updateSrv: UpdateSrv,
    val findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends WorkerConfigSrv {

  override val workerType: WorkerType.Type = WorkerType.analyzer

  def definitions: Future[Map[String, BaseConfig]] =
    buildDefinitionMap(workerSrv.listAnalyzerDefinitions._1)
}
