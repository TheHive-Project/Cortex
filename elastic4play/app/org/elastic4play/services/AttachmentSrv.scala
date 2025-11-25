package org.elastic4play.services

import java.io.InputStream
import java.nio.file.Files

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString
import com.sksamuel.elastic4s.ElasticDsl.search
import javax.inject.{Inject, Singleton}
import org.elastic4play.controllers.JsonFormat.{attachmentInputValueReads, fileInputValueFormat}
import org.elastic4play.controllers.{AttachmentInputValue, FileInputValue, JsonInputValue}
import org.elastic4play.database.{DBCreate, DBFind, DBRemove}
import org.elastic4play.models.{AttributeDef, BaseModelDef, EntityDef, ModelDef, AttributeFormat => F}
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.utils.{Hash, Hasher, Retry}
import org.elastic4play.{AttributeCheckingError, InvalidFormatAttributeError, MissingAttributeError}
import play.api.Configuration
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

case class Attachment(name: String, hashes: Seq[Hash], size: Long, contentType: String, id: String)

object Attachment {

  def apply(id: String, hashes: Seq[Hash], fiv: FileInputValue): Attachment =
    Attachment(fiv.name, hashes, Files.size(fiv.filepath), fiv.contentType, id)
}

trait AttachmentAttributes {
  _: AttributeDef =>
  val data: A[Array[Byte]] = attribute("binary", F.binaryFmt, "data")
}

@Singleton
class AttachmentModel(datastoreName: String)
    extends ModelDef[AttachmentModel, AttachmentChunk](datastoreName, "Attachment", "/datastore")
    with AttachmentAttributes {
  @Inject() def this(configuration: Configuration) = this(configuration.get[String]("datastore.name"))
}

class AttachmentChunk(model: AttachmentModel, attributes: JsObject)
    extends EntityDef[AttachmentModel, AttachmentChunk](model, attributes)
    with AttachmentAttributes

@Singleton
class AttachmentSrv(
    mainHash: String,
    extraHashes: Seq[String],
    chunkSize: Int,
    dbCreate: DBCreate,
    dbRemove: DBRemove,
    dbFind: DBFind,
    getSrv: GetSrv,
    findSrv: FindSrv,
    attachmentModel: AttachmentModel,
    implicit val system: ActorSystem,
    implicit val mat: Materializer
) {

  @Inject() def this(
      configuration: Configuration,
      dbCreate: DBCreate,
      dbRemove: DBRemove,
      getSrv: GetSrv,
      dbFind: DBFind,
      findSrv: FindSrv,
      attachmentModel: AttachmentModel,
      system: ActorSystem,
      mat: Materializer
  ) =
    this(
      configuration.get[String]("datastore.hash.main"),
      configuration.get[Seq[String]]("datastore.hash.extra"),
      configuration.underlying.getBytes("datastore.chunksize").toInt,
      dbCreate,
      dbRemove,
      dbFind,
      getSrv,
      findSrv,
      attachmentModel,
      system,
      mat
    )

  val mainHasher: Hasher   = Hasher(mainHash)
  val extraHashers: Hasher = Hasher(mainHash +: extraHashes: _*)

  /**
    * Handles attachments : send to datastore and build an object with hash and filename
    */
  def apply(model: BaseModelDef)(attributes: JsObject)(implicit ec: ExecutionContext): Future[JsObject] =
    // find all declared attribute as attachment in submitted data
    model.attachmentAttributes.foldLeft(Future.successful(attributes)) {
      case (attrs, (name, isRequired)) =>
        attrs.flatMap { a =>
          // try to convert in FileInputValue Scala Object
          val inputValue = (a \ name).asOpt[FileInputValue] orElse (a \ name).asOpt[AttachmentInputValue](attachmentInputValueReads)
          inputValue
            .map {
              // save attachment and replace FileInputValue json representation to JsObject containing attachment attributes
              case fiv: FileInputValue =>
                save(fiv).map { attachment =>
                  a - name + (name -> Json.toJson(attachment))
                }
              case aiv: AttachmentInputValue => Future.successful(a - name + (name -> Json.toJson(aiv.toAttachment)))
            }
            // if conversion to FileInputValue fails, it means that attribute is missing or format is invalid
            .getOrElse {
              (a \ name).asOpt[JsValue] match {
                case Some(v) if v != JsNull && v != JsArray(Nil) =>
                  Future.failed(
                    AttributeCheckingError(
                      model.modelName,
                      Seq(
                        InvalidFormatAttributeError(
                          name,
                          "attachment",
                          (a \ name).asOpt[FileInputValue].getOrElse(JsonInputValue((a \ name).as[JsValue]))
                        )
                      )
                    )
                  )
                case _ =>
                  if (isRequired)
                    Future.failed(AttributeCheckingError(model.modelName, Seq(MissingAttributeError(name))))
                  else
                    Future.successful(a)
              }
            }
        }
    }

  def save(filename: String, contentType: String, data: Array[Byte])(implicit ec: ExecutionContext): Future[Attachment] = {
    val hash   = mainHasher.fromByteArray(data).head.toString()
    val hashes = extraHashers.fromByteArray(data)

    for {
      attachment <- Retry()(classOf[Exception]) {
        getSrv[AttachmentModel, AttachmentChunk](attachmentModel, hash + "_0")
          .fallbackTo { // it it doesn't exist, create it
            Source
              .fromIterator(() => data.grouped(chunkSize))
              .zip(Source.unfold(0)(i => Some((i + 1) -> i)))
              .mapAsync(5) {
                case (buffer, index) =>
                  val data = java.util.Base64.getEncoder.encodeToString(buffer)
                  dbCreate(attachmentModel.modelName, None, Json.obj("binary" -> data, "_id" -> s"${hash}_$index"))
              }
              .runWith(Sink.ignore)
          }
          .map(_ => Attachment(filename, hashes, data.length, contentType, hash))
      }
    } yield attachment
  }

  def save(fiv: FileInputValue)(implicit ec: ExecutionContext): Future[Attachment] =
    for {
      hash   <- mainHasher.fromPath(fiv.filepath).map(_.head.toString())
      hashes <- extraHashers.fromPath(fiv.filepath)
      attachment <- Retry()(classOf[Exception]) {
        getSrv[AttachmentModel, AttachmentChunk](attachmentModel, hash + "_0")
          .fallbackTo { // it it doesn't exist, create it
            FileIO
              .fromPath(fiv.filepath, chunkSize)
              .zip(Source.fromIterator { () =>
                Iterator.iterate(0)(_ + 1)
              })
              .mapAsync(5) {
                case (buffer, index) =>
                  val data = java.util.Base64.getEncoder.encodeToString(buffer.toArray)
                  dbCreate(attachmentModel.modelName, None, Json.obj("binary" -> data, "_id" -> s"${hash}_$index"))
              }
              .runWith(Sink.ignore)
          }
          .map { _ =>
            Attachment(hash, hashes, fiv)
          }
      }
    } yield attachment

  def source(id: String)(implicit ec: ExecutionContext): Source[ByteString, NotUsed] =
    Source.unfoldAsync(0) { chunkNumber =>
      getSrv[AttachmentModel, AttachmentChunk](attachmentModel, s"${id}_$chunkNumber")
        .map { entity =>
          Some((chunkNumber + 1, ByteString(entity.data())))
        }
        .recover { case _ => None }
    }

  def stream(id: String)(implicit ec: ExecutionContext): InputStream = source(id).runWith(StreamConverters.asInputStream(1.minute))

  def getHashes(id: String)(implicit ec: ExecutionContext): Future[Seq[Hash]] = extraHashers.fromSource(source(id))

  def getSize(id: String)(implicit ec: ExecutionContext): Future[Int] = source(id).map(_.size).runFold(0)(_ + _)

  def attachmentUseCount(attachmentId: String)(implicit ec: ExecutionContext): Future[Long] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(None, "attachment.id" ~= attachmentId, Some("0-0"), Nil)._2
  }

  def delete(id: String)(implicit ec: ExecutionContext): Future[Unit] = {
    def removeChunks(chunkNumber: Int = 0): Future[Unit] =
      getSrv[AttachmentModel, AttachmentChunk](attachmentModel, s"${id}_$chunkNumber")
        .map { chunk =>
          dbRemove(chunk)
        }
        .flatMap { _ =>
          removeChunks(chunkNumber + 1)
        }

    removeChunks().recover { case _ => () }
  }

  def cleanup(implicit ec: ExecutionContext): Future[Unit] =
    dbFind(Some("all"), Nil)(index => search(index).matchQuery("relations", attachmentModel.modelName).fetchSource(false))
      ._1
      .mapConcat(o => (o \ "_id").asOpt[String].toList)
      .collect { case id if id.endsWith("_0") => id.dropRight(2) }
      .mapAsync(1) { id =>
        attachmentUseCount(id).map(id -> _)
      }
      .mapAsync(1) {
        case (id, 0L) => delete(id)
        case _        => Future.successful(())
      }
      .runWith(Sink.ignore)
      .map(_ => ())
}
