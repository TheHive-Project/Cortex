package org.thp.cortex.controllers

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import play.api.http.Status
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.elastic4play.NotFoundError

import javax.inject.{Inject, Named, Singleton}
import org.thp.cortex.models.{Job, JobStatus, Roles}
import org.thp.cortex.services.AuditActor.{JobEnded, Register}
import org.thp.cortex.services.JobSrv
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{QueryDSL, QueryDef}
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
    implicit val mat: Materializer,
    implicit val actorSystem: ActorSystem
) extends AbstractController(components)
    with Status {

  def list(dataTypeFilter: Option[String], dataFilter: Option[String], workerFilter: Option[String], range: Option[String]): Action[AnyContent] =
    authenticated(Roles.read).async { implicit request =>
      val (jobs, jobTotal) = jobSrv.listForUser(request.userId, dataTypeFilter, dataFilter, workerFilter, range)
      renderer.toOutput(OK, jobs, jobTotal)
    }

  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    import QueryDSL._
    val deleteFilter  = "status" ~!= "Deleted"
    val query         = request.body.getValue("query").fold[QueryDef](deleteFilter)(q => and(q.as[QueryDef], deleteFilter))
    val range         = request.body.getString("range")
    val sort          = request.body.getStrings("sort").getOrElse(Nil)
    val (jobs, total) = jobSrv.findForUser(request.userId, query, range, sort)
    renderer.toOutput(OK, jobs, total)

  }

  def get(jobId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request =>
    jobSrv.getForUser(request.userId, jobId).map { job =>
      renderer.toOutput(OK, job)
    }
  }

  def delete(jobId: String): Action[AnyContent] = authenticated(Roles.analyze, Roles.orgAdmin).async { implicit request =>
    jobSrv
      .getForUser(request.userId, jobId)
      .flatMap(job => jobSrv.delete(job))
      .map(_ => NoContent)
  }

  def createResponderJob(workerId: String): Action[Fields] = authenticated(Roles.analyze).async(fieldsBodyParser) { implicit request =>
    val fields = request.body
    val fieldsWithStringData = fields.getValue("data") match {
      case Some(d) => fields.set("data", d.toString)
      case None    => fields
    }
    jobSrv
      .create(workerId, fieldsWithStringData)
      .map { job =>
        renderer.toOutput(OK, job)
      }
  }

  def createAnalyzerJob(workerId: String): Action[Fields] = authenticated(Roles.analyze).async(fieldsBodyParser) { implicit request =>
    jobSrv
      .create(workerId, request.body)
      .map { job =>
        renderer.toOutput(OK, job)
      }
  }

  private def getJobWithReport(userId: String, jobId: String): Future[JsValue] =
    jobSrv.getForUser(userId, jobId).flatMap(getJobWithReport(userId, _))

  private def getJobWithReport(userId: String, job: Job): Future[JsValue] =
    (job.status() match {
      case JobStatus.Success =>
        for {
          report <- jobSrv.getReport(job)
          (artifactSource, _) = jobSrv.findArtifacts(userId, job.id, QueryDSL.any, Some("all"), Nil)
          artifacts <- artifactSource
            .collect {
              case artifact if artifact.data().isDefined =>
                Json.obj(
                  "data"     -> artifact.data(),
                  "dataType" -> artifact.dataType(),
                  "message"  -> artifact.message(),
                  "tags"     -> artifact.tags(),
                  "tlp"      -> artifact.tlp()
                )
              case artifact if artifact.attachment().isDefined =>
                val attachment = artifact.attachment().get
                Json.obj(
                  "dataType" -> artifact.dataType(),
                  "message"  -> artifact.message(),
                  "tags"     -> artifact.tags(),
                  "tlp"      -> artifact.tlp(),
                  "attachment" -> Json
                    .obj("contentType" -> attachment.contentType, "id" -> attachment.id, "name" -> attachment.name, "size" -> attachment.size)
                )
            }
            .runWith(Sink.seq)
        } yield Json.obj(
          "summary"    -> Json.parse(report.summary()),
          "full"       -> Json.parse(report.full()),
          "success"    -> true,
          "artifacts"  -> artifacts,
          "operations" -> Json.parse(report.operations())
        )
      case JobStatus.Failure =>
        val errorMessage = job.errorMessage().getOrElse("")
        Future.successful(Json.obj("errorMessage" -> errorMessage, "input" -> job.input(), "success" -> false))
      case JobStatus.InProgress => Future.successful(JsString("Running"))
      case JobStatus.Waiting    => Future.successful(JsString("Waiting"))
      case JobStatus.Deleted    => Future.successful(JsString("Deleted"))
    }).map { report =>
      Json.toJson(job).as[JsObject] + ("report" -> report)
    }

  def report(jobId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request =>
    getJobWithReport(request.userId, jobId).map(Ok(_))
  }

  def waitReport(jobId: String, atMost: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request =>
    jobSrv
      .getForUser(request.userId, jobId)
      .flatMap {
        case job if job.status() == JobStatus.InProgress || job.status() == JobStatus.Waiting =>
          val duration                  = Duration(atMost).asInstanceOf[FiniteDuration]
          implicit val timeout: Timeout = Timeout(duration + 1.second)
          (auditActor ? Register(jobId, duration))
            .mapTo[JobEnded]
            .map(_ => ())
            .withTimeout(duration, ())
            .flatMap(_ => getJobWithReport(request.userId, jobId))
        case job =>
          getJobWithReport(request.userId, job)
      }
      .map(Ok(_))
  }

  def getJobStatus: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val jobIds = request.body.getStrings("jobIds").getOrElse(Nil)
    Future
      .traverse(jobIds) { jobId =>
        jobSrv
          .getForUser(request.userId, jobId)
          .map(j => jobId -> JsString(j.status().toString))
          .recover {
            case _: NotFoundError => jobId -> JsString("NotFound")
            case error            => jobId -> JsString(s"Error($error)")
          }
      }
      .map(statuses => Ok(JsObject(statuses)))
  }

  def listArtifacts(jobId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val query              = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range              = request.body.getString("range")
    val sort               = request.body.getStrings("sort").getOrElse(Nil)
    val (artifacts, total) = jobSrv.findArtifacts(request.userId, jobId, query, range, sort)
    renderer.toOutput(OK, artifacts, total)
  }
}
