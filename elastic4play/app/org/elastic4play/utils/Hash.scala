package org.elastic4play.utils

import java.nio.charset.Charset
import java.nio.file.{Path, Paths}
import java.security.MessageDigest

import scala.concurrent.{ExecutionContext, Future}

import play.api.Logger
import play.api.libs.json.JsValue

import org.apache.pekko.stream.scaladsl.{FileIO, Sink, Source}
import org.apache.pekko.stream.{IOResult, Materializer}
import org.apache.pekko.util.ByteString

// TODO use play.api.libs.Codecs

case class Hasher(algorithms: String*) {

  def fromPath(path: Path)(implicit mat: Materializer, ec: ExecutionContext): Future[Seq[Hash]] =
    fromSource(FileIO.fromPath(path))

  def fromSource(source: Source[ByteString, Any])(implicit mat: Materializer, ec: ExecutionContext): Future[Seq[Hash]] = {
    val mds = algorithms.map(algo => MessageDigest.getInstance(algo))
    source
      .runForeach { bs =>
        mds.foreach(md => md.update(bs.toByteBuffer))
      }
      .map { _ =>
        mds.map(md => Hash(md.digest()))
      }
  }

  def fromString(data: String): Seq[Hash] =
    fromByteArray(data.getBytes(Charset.forName("UTF8")))

  def fromByteArray(data: Array[Byte]): Seq[Hash] = {
    val mds = algorithms.map(algo => MessageDigest.getInstance(algo))
    mds.map(md => Hash(md.digest(data)))
  }

}

class MultiHash(algorithms: String)(implicit mat: Materializer, ec: ExecutionContext) {
  private[MultiHash] lazy val logger = Logger(getClass)
  private val md                     = MessageDigest.getInstance(algorithms)

  def addValue(value: JsValue): Unit = {
    md.update(0.asInstanceOf[Byte])
    md.update(value.toString.getBytes)
  }

  def addFile(filename: String): Future[IOResult] =
    addFile(FileIO.fromPath(Paths.get(filename))).flatMap(identity)

  def addFile[A](source: Source[ByteString, A]): Future[A] = {
    md.update(0.asInstanceOf[Byte])
    source
      .toMat(Sink.foreach { bs =>
        md.update(bs.toByteBuffer)
      })((a, done) => done.map(_ => a))
      .run()
  }
  def digest: Hash = Hash(md.digest())
}

case class Hash(data: Array[Byte]) {
  override def toString: String = data.map(b => "%02x".format(b)).mkString
}

object Hash {

  def apply(s: String): Hash = Hash {
    s.grouped(2)
      .map { cc =>
        (Character.digit(cc(0), 16) << 4 | Character.digit(cc(1), 16)).toByte
      }
      .toArray
  }
}
