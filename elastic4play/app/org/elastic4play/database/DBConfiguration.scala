package org.elastic4play.database

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.pekko.reactivestreams.ReactiveElastic.ReactiveElastic
import com.sksamuel.elastic4s.pekko.reactivestreams.{RequestBuilder, ResponseListener}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.BulkResponseItem
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchRequest}
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elastic4play.{ConflictError, IndexNotFoundException, InternalError, SearchError}
import org.elasticsearch.client.RestClientBuilder.{HttpClientConfigCallback, RequestConfigCallback}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.JsObject
import play.api.{Configuration, Logger}

import java.nio.file.{Files, Paths}
import java.security.KeyStore
import javax.inject.{Inject, Named, Singleton}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._

/**
  * This class is a wrapper of ElasticSearch client from Elastic4s
  * It builds the client using configuration (ElasticSearch addresses, cluster and index name)
  * It add timed annotation in order to measure storage metrics
  */
@Singleton
class DBConfiguration @Inject() (
    config: Configuration,
    lifecycle: ApplicationLifecycle,
    @Named("databaseVersion") val version: Int,
    implicit val actorSystem: ActorSystem
) {
  private[DBConfiguration] lazy val logger = Logger(getClass)

  private def requestConfigCallback: RequestConfigCallback = (requestConfigBuilder: RequestConfig.Builder) => {
    requestConfigBuilder.setAuthenticationEnabled(credentialsProviderMaybe.isDefined)
    config.getOptional[Boolean]("search.circularRedirectsAllowed").foreach(requestConfigBuilder.setCircularRedirectsAllowed)
    config.getOptional[Int]("search.connectionRequestTimeout").foreach(requestConfigBuilder.setConnectionRequestTimeout)
    config.getOptional[Int]("search.connectTimeout").foreach(requestConfigBuilder.setConnectTimeout)
    config.getOptional[Boolean]("search.contentCompressionEnabled").foreach(requestConfigBuilder.setContentCompressionEnabled)
    config.getOptional[String]("search.cookieSpec").foreach(requestConfigBuilder.setCookieSpec)
    config.getOptional[Boolean]("search.expectContinueEnabled").foreach(requestConfigBuilder.setExpectContinueEnabled)
    //    config.getOptional[InetAddress]("search.localAddress").foreach(requestConfigBuilder.setLocalAddress)
    config.getOptional[Int]("search.maxRedirects").foreach(requestConfigBuilder.setMaxRedirects)
    //    config.getOptional[Boolean]("search.proxy").foreach(requestConfigBuilder.setProxy)
    config.getOptional[Seq[String]]("search.proxyPreferredAuthSchemes").foreach(v => requestConfigBuilder.setProxyPreferredAuthSchemes(v.asJava))
    config.getOptional[Boolean]("search.redirectsEnabled").foreach(requestConfigBuilder.setRedirectsEnabled)
    config.getOptional[Boolean]("search.relativeRedirectsAllowed").foreach(requestConfigBuilder.setRelativeRedirectsAllowed)
    config.getOptional[Int]("search.socketTimeout").foreach(requestConfigBuilder.setSocketTimeout)
    config.getOptional[Seq[String]]("search.targetPreferredAuthSchemes").foreach(v => requestConfigBuilder.setTargetPreferredAuthSchemes(v.asJava))
    requestConfigBuilder
  }

  private lazy val credentialsProviderMaybe: Option[CredentialsProvider] =
    for {
      user     <- config.getOptional[String]("search.user")
      password <- config.getOptional[String]("search.password")
    } yield {
      val provider    = new BasicCredentialsProvider
      val credentials = new UsernamePasswordCredentials(user, password)
      provider.setCredentials(AuthScope.ANY, credentials)
      provider
    }

  private lazy val sslContextMaybe: Option[SSLContext] = config.getOptional[String]("search.keyStore.path").map { keyStore =>
    val keyStorePath     = Paths.get(keyStore)
    val keyStoreType     = config.getOptional[String]("search.keyStore.type").getOrElse(KeyStore.getDefaultType)
    val keyStorePassword = config.getOptional[String]("search.keyStore.password").getOrElse("").toCharArray
    val keyInputStream   = Files.newInputStream(keyStorePath)
    val keyManagers =
      try {
        val keyStore = KeyStore.getInstance(keyStoreType)
        keyStore.load(keyInputStream, keyStorePassword)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        kmf.init(keyStore, keyStorePassword)
        kmf.getKeyManagers
      } finally {
        keyInputStream.close()
      }

    val trustManagers = config
      .getOptional[String]("search.trustStore.path")
      .map { trustStorePath =>
        val keyStoreType       = config.getOptional[String]("search.trustStore.type").getOrElse(KeyStore.getDefaultType)
        val trustStorePassword = config.getOptional[String]("search.trustStore.password").getOrElse("").toCharArray
        val trustInputStream   = Files.newInputStream(Paths.get(trustStorePath))
        try {
          val keyStore = KeyStore.getInstance(keyStoreType)
          keyStore.load(trustInputStream, trustStorePassword)
          val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
          tmf.init(keyStore)
          tmf.getTrustManagers
        } finally {
          trustInputStream.close()
        }
      }
      .getOrElse(Array.empty)

    // Configure the SSL context to use TLS
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagers, trustManagers, null)
    sslContext
  }

  private def httpClientConfig: HttpClientConfigCallback = (httpClientBuilder: HttpAsyncClientBuilder) => {
    sslContextMaybe.foreach(httpClientBuilder.setSSLContext)
    credentialsProviderMaybe.foreach(httpClientBuilder.setDefaultCredentialsProvider)
    httpClientBuilder
  }

  /**
    * Underlying ElasticSearch client
    */
  private[database] val props   = ElasticProperties(config.get[String]("search.uri"))
  private[database] var clients = Map.empty[ExecutionContext, ElasticClient]
  private[database] def getClient(ec: ExecutionContext): ElasticClient =
    clients.get(ec) match {
      case Some(c) => c
      case None =>
        synchronized {
          val c = clients.getOrElse(ec, ElasticClient(JavaClient(props, requestConfigCallback, httpClientConfig)))
          clients = clients + (ec -> c)
          c
        }
    }
  // when application close, close also ElasticSearch connection
  lifecycle.addStopHook { () =>
    clients.values.foreach(_.close())
    Future.successful(())
  }

  //noinspection ScalaUnusedSymbol
  def execute[T, U](t: T)(
      implicit
      handler: Handler[T, U],
      manifest: Manifest[U],
      ec: ExecutionContext
  ): Future[U] = {
    val client = getClient(ec)
    logger.debug(s"Elasticsearch request: ${client.show(t)}")
    client.execute(t).flatMap {
      case RequestSuccess(_, _, _, r) => Future.successful(r)
      case RequestFailure(_, _, _, error) =>
        val exception = error.`type` match {
          case "index_not_found_exception"         => IndexNotFoundException
          case "version_conflict_engine_exception" => ConflictError(error.reason, JsObject.empty)
          case "search_phase_execution_exception"  => SearchError(error.reason)
          case _                                   => InternalError(s"Unknown error: $error")
        }
        exception match {
          case _: ConflictError =>
          case _                => logger.error(s"ElasticSearch request failure: ${client.show(t)}\n => $error")
        }
        Future.failed(exception)
    }
  }

  /**
    * Creates a Source (akka stream) from the result of the search
    */
  def source(searchRequest: SearchRequest)(implicit ec: ExecutionContext): Source[SearchHit, NotUsed] =
    Source.fromPublisher(getClient(ec).publisher(searchRequest))

  /**
    * Create a Sink (akka stream) that create entity in ElasticSearch
    */
  def sink[T](implicit builder: RequestBuilder[T], ec: ExecutionContext): Sink[T, Future[Unit]] = {
    val sinkListener = new ResponseListener[T] {
      override def onAck(resp: BulkResponseItem, original: T): Unit = ()

      override def onFailure(resp: BulkResponseItem, original: T): Unit =
        logger.warn(s"Document index failure ${resp.id}: ${resp.error.fold("unexpected")(_.toString)}\n$original")
    }
    val end = Promise[Unit]()
    val complete = () => {
      if (!end.isCompleted)
        end.success(())
      ()
    }
    val failure = (t: Throwable) => {
      end.failure(t)
      ()
    }
    Sink
      .fromSubscriber(
        getClient(ec).subscriber(
          batchSize = 100,
          concurrentRequests = 5,
          refreshAfterOp = false,
          listener = sinkListener,
          typedListener = ResponseListener.noop,
          completionFn = complete,
          errorFn = failure,
          flushInterval = None,
          flushAfter = None,
          failureWait = 2.seconds,
          maxAttempts = 10
        )
      )
      .mapMaterializedValue { _ =>
        end.future
      }
  }

  /**
    * Name of the index, suffixed by the current version
    */
  val indexName: String = config.get[String]("search.index") + "_" + version

  /**
    * return a new instance of DBConfiguration that points to the previous version of the index schema
    */
  def previousVersion: DBConfiguration =
    new DBConfiguration(config, lifecycle, version - 1, actorSystem)
}
