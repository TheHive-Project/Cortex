package org.elastic4play.database

import ch.qos.logback.classic.spi.{ILoggingEvent, ThrowableProxy, ThrowableProxyUtil}
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import com.sksamuel.elastic4s.ElasticDsl._
import com.sun.net.httpserver.{HttpsConfigurator, HttpsExchange, HttpsParameters, HttpsServer}
import org.apache.pekko.actor.ActorSystem
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.Configuration
import play.api.inject.DefaultApplicationLifecycle

import java.net.{InetAddress, InetSocketAddress, URL}
import java.nio.file.{Files, Paths}
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

/**
  * Tests of the Elasticsearch TLS configuration (`search.keyStore` / `search.trustStore`, DL-7123).
  * A real handshake is done against the JDK built-in HttpsServer, so no Elasticsearch is needed.
  * Fixtures are in `test/resources/tls` (regenerate them with `gen.sh`).
  */
@RunWith(classOf[JUnitRunner])
class DBConfigurationSpec extends Specification {
  // the log appender is attached to a global logger
  sequential

  import DBConfigurationSpec._

  "DBConfiguration.buildSslContext" should {

    // UT-1
    "return no context when neither keyStore nor trustStore is configured" in {
      val result = run(Configuration.empty, TlsServer)

      result.context must beNone
      result.warnings must beEmpty($conforms)
    }

    // UT-2
    "use the custom trust when only a trustStore is configured" in {
      foreach(Seq("truststore.jks" -> "JKS", "truststore.p12" -> "PKCS12")) {
        case (file, storeType) =>
          // Given
          val config = trustStore(file, TsPass, Some(storeType))

          // When
          val result = run(config, TlsServer)

          // Then
          (result.context must beSome) and (result.handshake must beSuccessfulTry) and (result.warnings must beEmpty($conforms))
      }
    }

    // UT-3
    "load a PKCS12 trustStore when no type is given" in {
      val result = run(trustStore("truststore.p12", TsPass, None), TlsServer)

      (result.context must beSome) and (result.handshake must beSuccessfulTry) and (result.warnings must beEmpty($conforms))
    }

    "load a JKS trustStore when no type is given (JDK PKCS12 keystore reads JKS in compatibility mode)" in {
      val result = run(trustStore("truststore.jks", TsPass, None), TlsServer)

      (result.context must beSome) and (result.handshake must beSuccessfulTry) and (result.warnings must beEmpty($conforms))
    }

    // UT-4
    "reject a server certificate which is not signed by the CA of the trustStore" in {
      val result = run(trustStore("other-truststore.jks", TsPass, Some("JKS")), TlsServer)

      (result.context must beSome) and (result.handshake must beFailedTry) and (result.handshakeError must contain("PKIX path building failed"))
    }

    // UT-5
    "use the JVM default trust when only a keyStore is configured" in {
      // Given the server certificate is signed by a private CA that the JVM default does not trust
      val result = run(keyStore("client.p12", KsPass, Some("PKCS12")), TlsServer)

      // Then the default trust manager is used (develop gave an empty TrustManager array)
      (result.context must beSome) and
        (result.handshakeError must contain("PKIX path building failed")) and
        (result.handshakeError must not(contain("No X509TrustManager implementation available")))
    }

    // UT-6
    "do mTLS when both keyStore and trustStore are configured" in {
      val config = keyStore("client.jks", KsPass, Some("JKS")).withFallback(trustStore("truststore.jks", TsPass, Some("JKS")))

      val result = run(config, MtlsServer)

      (result.handshake must beSuccessfulTry) and
        (result.clientSubjects must contain(exactly("CN=cortex-client, O=Cortex Tests"))) and
        (result.warnings must beEmpty($conforms))
    }

    // UT-7
    "not send any client certificate when only a trustStore is configured" in {
      val result = run(trustStore("truststore.jks", TsPass, Some("JKS")), MtlsServer)

      (result.context must beSome) and (result.handshake must beFailedTry) and (result.clientSubjects must beEmpty($conforms)) and (result.warnings must beEmpty(
        $conforms
      ))
    }
  }

  "DBConfiguration.buildSslContext with a store which cannot be loaded" should {
    // UT-8, UT-9: a configured store which cannot be loaded refuses the start
    val trust = trustStore("truststore.jks", TsPass, Some("JKS"))
    val cases = Seq(
      ("trustStore", "does not exist", trustStore(missing("missing.jks"), TsPass, Some("JKS")), Seq("NoSuchFileException")),
      ("trustStore", "has a wrong password", trustStore("truststore.jks", WrongPass, Some("JKS")), Seq("IOException")),
      ("trustStore", "is corrupt", trustStore("corrupt.jks", TsPass, Some("JKS")), Seq("IOException")),
      ("keyStore", "does not exist", keyStore(missing("missing.jks"), KsPass, Some("JKS")).withFallback(trust), Seq("NoSuchFileException")),
      ("keyStore", "has a wrong password", keyStore("client.jks", WrongPass, Some("JKS")).withFallback(trust), Seq("IOException")),
      (
        "keyStore",
        "has a key password different from the store password",
        keyStore("client-keypass.jks", KsPass, Some("JKS")).withFallback(trust),
        Seq("UnrecoverableKeyException")
      )
    )
    cases.foreach {
      case (kind, name, config, causeType) =>
        s"fail with the store path and its cause when the $kind $name" in {
          val error = Try(DBConfiguration.buildSslContext(config)).failed.get

          (error must beAnInstanceOf[IllegalArgumentException]) and
            (error.getMessage must contain(s"Failed to load $kind from")) and
            (error.getMessage must contain(config.get[String](s"search.$kind.path"))) and
            (causes(error).map(_.getClass.getSimpleName) must contain(causeType.head)) and
            // UT-10
            (leakedPasswords(error.getMessage + ThrowableProxyUtil.asString(new ThrowableProxy(error))) must beEmpty($conforms))
        }
    }
    br
  }

  // UT-12
  "DBConfiguration.buildSslContext without password" should {
    "refuse a JKS trustStore created with a password" in {
      val config = Configuration.from(Map("search.trustStore.path" -> tls("truststore.jks"), "search.trustStore.type" -> "JKS"))

      Try(DBConfiguration.buildSslContext(config)).failed.get.getMessage must contain("Failed to load trustStore from")
    }
  }

  // UT-11
  "DBConfiguration" should {
    "fail to be built, not at the first request, when a configured store cannot be loaded" in {
      val config = keyStore(missing("missing.jks"), KsPass, Some("JKS")).withFallback(
        Configuration.from(Map("search.uri" -> "https://localhost:1", "search.index" -> "cortex_test"))
      )
      val actorSys = ActorSystem("DBConfigurationSpecStart")
      try new DBConfiguration(config, new DefaultApplicationLifecycle(), 1, actorSys) must throwAn[IllegalArgumentException](
        "Failed to load keyStore from"
      )
      finally Await.ready(actorSys.terminate(), 10.seconds)
    }

    "give the SSL context to the Elasticsearch client" in {
      val server    = new TestServer(mtls = false)
      val actorSys  = ActorSystem("DBConfigurationSpec")
      val lifecycle = new DefaultApplicationLifecycle()
      lazy val db   = new DBConfiguration(trustedConfig, lifecycle, 1, actorSys)
      lazy val trustedConfig =
        trustStore("truststore.jks", TsPass, Some("JKS"))
          .withFallback(Configuration.from(Map("search.uri" -> s"https://localhost:${server.port}", "search.index" -> "cortex_test")))
      try {
        // Given
        // When
        val request = db.getClient(ExecutionContext.global).execute(clusterHealth())
        Try(Await.ready(request, 20.seconds))

        // Then the TLS handshake succeeded and the request reached the server (the response body is not an Elasticsearch one)
        server.requests.get() must beGreaterThanOrEqualTo(1)
      } finally {
        db.clients.values.foreach(_.close())
        server.close()
        Await.ready(actorSys.terminate(), 10.seconds)
      }
    }
  }
}

object DBConfigurationSpec {
  val TsPass    = "ts-pass-7123"
  val KsPass    = "ks-pass-7123"
  val KeyPass   = "kp-pass-7123"
  val SrvPass   = "srv-pass-7123"
  val WrongPass = "wrong-pass-7123"

  private val passwords = Seq(TsPass, KsPass, KeyPass, SrvPass, WrongPass)

  sealed trait ServerMode
  case object TlsServer  extends ServerMode
  case object MtlsServer extends ServerMode

  /** Absolute path of a fixture */
  def tls(name: String): String = Paths.get(getClass.getResource(s"/tls/$name").toURI).toString

  /** Path inside the fixture directory which does not exist */
  def missing(name: String): String = Paths.get(tls("gen.sh")).resolveSibling(name).toString

  // a fixture name is resolved, an absolute path (from `missing`) is kept
  private def resolve(file: String): String = if (Paths.get(file).isAbsolute) file else tls(file)

  private def store(kind: String, file: String, password: String, storeType: Option[String]): Configuration =
    Configuration.from(
      Map(s"search.$kind.path" -> resolve(file), s"search.$kind.password" -> password) ++ storeType.map(t => s"search.$kind.type" -> t)
    )

  def trustStore(file: String, password: String, storeType: Option[String]): Configuration = store("trustStore", file, password, storeType)
  def keyStore(file: String, password: String, storeType: Option[String]): Configuration   = store("keyStore", file, password, storeType)

  case class RunResult(
      context: Option[SSLContext],
      handshake: Try[Unit],
      clientSubjects: Seq[String],
      warnings: Seq[ILoggingEvent],
      logText: String
  ) {
    def handshakeError: String = handshake match {
      case Failure(e) => causes(e).map(c => s"${c.getClass.getName}: ${c.getMessage}").mkString(" <- ")
      case Success(_) => "no error"
    }
  }

  private def causes(t: Throwable): Seq[Throwable] = Iterator.iterate(t)(_.getCause).takeWhile(_ != null).toSeq

  def leakedPasswords(text: String): Seq[String] = passwords.filter(text.contains)

  /** Build the SSL context under test, then do a handshake with a local HTTPS server. */
  def run(config: Configuration, mode: ServerMode): RunResult = {
    val appender = new ListAppender[ILoggingEvent]
    val logger   = LoggerFactory.getLogger("org.elastic4play.database.DBConfiguration").asInstanceOf[LogbackLogger]
    appender.start()
    logger.addAppender(appender)
    val context =
      try DBConfiguration.buildSslContext(config)
      finally logger.detachAppender(appender)

    val events = appender.list.asScala.toSeq
    val server = new TestServer(mtls = mode == MtlsServer)
    try {
      val handshake = context.fold[Try[Unit]](Success(()))(ctx => server.get(ctx))
      RunResult(
        context,
        handshake,
        server.clientSubjects.asScala.toSeq,
        events.filter(_.getLevel == Level.WARN),
        events.map(e => e.getFormattedMessage + Option(e.getThrowableProxy).fold("")(ThrowableProxyUtil.asString)).mkString("\n")
      )
    } finally server.close()
  }

  /** HTTPS server on localhost with the `server.p12` certificate. In mTLS mode, a client certificate signed by `ca` is required. */
  class TestServer(mtls: Boolean) extends AutoCloseable {
    val clientSubjects = new ConcurrentLinkedQueue[String]
    val requests       = new java.util.concurrent.atomic.AtomicInteger(0)

    private def load(file: String, password: String, storeType: String): KeyStore =
      Using.resource(Files.newInputStream(Paths.get(tls(file)))) { in =>
        val ks = KeyStore.getInstance(storeType)
        ks.load(in, password.toCharArray)
        ks
      }

    private val sslContext = {
      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      kmf.init(load("server.p12", SrvPass, "PKCS12"), SrvPass.toCharArray)
      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(load("truststore.jks", TsPass, "JKS"))
      val ctx = SSLContext.getInstance("TLS")
      ctx.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
      ctx
    }

    private val server = {
      val s = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("localhost"), 0), 0)
      s.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
        override def configure(params: HttpsParameters): Unit = {
          val p = getSSLContext.getDefaultSSLParameters
          p.setNeedClientAuth(mtls)
          params.setSSLParameters(p)
        }
      })
      s.createContext(
        "/",
        exchange => {
          requests.incrementAndGet()
          // no peer certificate (TLS mode) is reported as an SSLPeerUnverifiedException
          Try(exchange.asInstanceOf[HttpsExchange].getSSLSession.getPeerCertificates).foreach(_.headOption.foreach {
            case cert: X509Certificate => clientSubjects.add(cert.getSubjectX500Principal.getName("RFC1779"))
            case _                     =>
          })
          val body = "{}".getBytes("UTF-8")
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(200, body.length.toLong)
          exchange.getResponseBody.write(body)
          exchange.close()
        }
      )
      s.start()
      s
    }

    val port: Int = server.getAddress.getPort

    /** Do a GET with the given context, failure if the handshake or the exchange fails */
    def get(ctx: SSLContext): Try[Unit] = Try {
      val connection = new URL(s"https://localhost:$port/").openConnection().asInstanceOf[HttpsURLConnection]
      connection.setSSLSocketFactory(ctx.getSocketFactory)
      connection.setConnectTimeout(5000)
      connection.setReadTimeout(5000)
      try {
        connection.getResponseCode
        ()
      } finally connection.disconnect()
    }

    def close(): Unit = server.stop(0)
  }
}
