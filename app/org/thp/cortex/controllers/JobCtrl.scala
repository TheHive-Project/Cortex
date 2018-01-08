package org.thp.cortex.controllers

import javax.inject.{ Inject, Named, Singleton }

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }

import play.api.http.Status
import play.api.libs.json.{ JsObject, JsString, JsValue, Json }
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import akka.util.Timeout
import org.thp.cortex.models.{ Job, JobStatus, Roles }
import org.thp.cortex.services.AuditActor.{ JobEnded, Register }
import org.thp.cortex.services.JobSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.utils.RichFuture

@Singleton
class JobCtrl @Inject() (
    jobSrv: JobSrv,
    @Named("audit") auditActor: ActorRef,
    fieldsBodyParser: FieldsBodyParser,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val actorSystem: ActorSystem) extends AbstractController(components) with Status {

  def list(dataTypeFilter: Option[String], dataFilter: Option[String], analyzerFilter: Option[String], range: Option[String]): Action[AnyContent] = authenticated(Roles.read).async { request ⇒
    val (jobs, jobTotal) = jobSrv.list(dataTypeFilter, dataFilter, analyzerFilter, range)
    renderer.toOutput(OK, jobs, jobTotal)
  }

  def get(jobId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit authContext ⇒
    jobSrv.get(jobId).map { job ⇒
      renderer.toOutput(OK, job)
    }
  }

  def create(analyzerId: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    jobSrv.create(analyzerId, request.body)
      .map { job ⇒
        renderer.toOutput(OK, job)
      }
  }
  //  def remove(jobId: String): Action[AnyContent] = Action.async { request ⇒
  //    jobSrv.remove(jobId).map(_ ⇒ Ok(""))
  //  }

  private def getJobWithReport(jobId: String): Future[JsValue] = {
    jobSrv.get(jobId).flatMap(getJobWithReport)
  }

  private def getJobWithReport(job: Job): Future[JsValue] = {
    (job.status() match {
      case JobStatus.Success    ⇒ jobSrv.getReport(job).map(Json.toJson(_))
      case JobStatus.InProgress ⇒ Future.successful(JsString("Running"))
      case JobStatus.Failure    ⇒ Future.successful(JsString(job.errorMessage().getOrElse("error")))
      case JobStatus.Waiting    ⇒ Future.successful(JsString("Waiting"))
    })
      .map { report ⇒
        Json.toJson(job).as[JsObject] + ("report" -> report)
      }
  }

  def report(jobId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit authContext ⇒
    getJobWithReport(jobId).map(Ok(_))
  }

  def waitReport(jobId: String, atMost: String): Action[AnyContent] = authenticated(Roles.read).async { implicit authContext ⇒
    jobSrv.get(jobId)
      .flatMap {
        case job if job.status == JobStatus.InProgress || job.status == JobStatus.Waiting ⇒
          val duration = Duration(atMost).asInstanceOf[FiniteDuration]
          implicit val timeout = Timeout(duration)
          (auditActor ? Register(jobId, duration))
            .mapTo[JobEnded]
            .map(_ ⇒ ())
            .withTimeout(duration, ())
            .flatMap(_ ⇒ getJobWithReport(jobId))
        case job ⇒ getJobWithReport(job)
      }
      .map(Ok(_))
  }
}