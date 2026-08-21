import sbt.*

object Dependencies {
  val scalaVersion        = "2.13.17"
  val dockerJavaVersion   = "3.6.0"
  val pekkoVersion        = "1.2.1"
  val nettyVersion        = "4.1.135.Final"
  val bouncyCastleVersion = "1.84"
  val vertxVersion        = "4.5.27"
  val lz4Version          = "1.11.0"
  val jacksonVersion      = "2.22.0"

  object Play {
    val version: String = play.core.PlayVersion.current
    val ws              = "org.playframework" %% "play-ws" % version exclude ("org.playframework", "play-ws-standalone-xml")
    val ahc             = "org.playframework" %% "play-ahc-ws" % version
    val test            = "org.playframework" %% "play-test" % version
    val specs2          = "org.playframework" %% "play-specs2" % version
    val filters         = "org.playframework" %% "filters-helpers" % version
    val guice           = "org.playframework" %% "play-guice" % version
    val twirl           = "org.playframework" %% "twirl-api" % "1.6.10"
  }

  val scalaGuice = "net.codingwell" %% "scala-guice" % "6.0.0"

  val reflections         = "org.reflections"        % "reflections"                   % "0.10.2"
  val zip4j               = "net.lingala.zip4j"      % "zip4j"                         % "2.11.5"
  // RFC 6238 TOTP (multi-factor authentication). Its only transitives (commons-codec,
  // httpclient) are already on the classpath at higher versions via elasticsearch-rest-client
  // and docker-java, so they are evicted upward and this adds a single jar.
  val googleAuth          = "com.warrenstrange"      % "googleauth"                    % "1.5.0"
  // QR code encoding for MFA enrolment. Only the "core" module, which has no transitive
  // dependencies: the image writers live in zxing "javase", which we don't use because the
  // QR is rendered to SVG directly.
  val zxing               = "com.google.zxing"       % "core"                          % "3.5.4"
  val dockerJavaClient    = "com.github.docker-java" % "docker-java-core"              % dockerJavaVersion
  val dockerJavaTransport = "com.github.docker-java" % "docker-java-transport-zerodep" % dockerJavaVersion
  val k8sClient           = "io.fabric8"             % "kubernetes-client"             % "7.4.0"
  val lz4Java             = "at.yawk.lz4"            % "lz4-java"                      % lz4Version
  val pekkoCluster        = "org.apache.pekko"       %% "pekko-cluster"                % pekkoVersion
  val pekkoClusterTyped   = "org.apache.pekko"       %% "pekko-cluster-typed"          % pekkoVersion
}
