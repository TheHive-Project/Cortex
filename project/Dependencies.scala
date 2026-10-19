import sbt.*

object Dependencies {
  val scalaVersion        = "2.13.18"
  val dockerJavaVersion   = "3.7.1"
  val pekkoVersion        = "1.2.1" // Play 3.0 ships Pekko 1.0.x, we run a newer 1.x (binary compatible within 1.x).
  val nettyVersion        = "4.1.139.Final" // vertx 4.5 wants netty 4.1.X, can not upgrade to 4.2.x
  val bouncyCastleVersion = "1.86"
  val vertxVersion        = "4.5.35" // Stays in 4.5.x for now for fabric8 7.x.
  val jacksonVersion      = "2.22.3"
  val jacksonScalaVersion = s"$jacksonVersion.1"

  object Play {
    val version: String = play.core.PlayVersion.current
    val ws              = "org.playframework" %% "play-ws" % version
    val ahc             = "org.playframework" %% "play-ahc-ws" % version
    val specs2          = "org.playframework" %% "play-specs2" % version
    val guice           = "org.playframework" %% "play-guice" % version
  }

  val scalaGuice = "net.codingwell" %% "scala-guice" % "6.0.0" // v7 moves to jakarta.inject, Play 3.0 is on javax.inject

  val reflections         = "org.reflections"        % "reflections"                       % "0.10.2"
  val zip4j               = "net.lingala.zip4j"      % "zip4j"                             % "2.11.6"
  val dockerJavaClient    = "com.github.docker-java" % "docker-java-core"                  % dockerJavaVersion
  val dockerJavaTransport = "com.github.docker-java" % "docker-java-transport-httpclient5" % dockerJavaVersion // not zerodep: it shades its own HttpComponents, which we can't upgrade
  val k8sClient           = "io.fabric8"             % "kubernetes-client"                 % "7.9.0" // v8 needs JDK17
  val pekkoCluster        = "org.apache.pekko"       %% "pekko-cluster"                    % pekkoVersion
  val pekkoClusterTyped   = "org.apache.pekko"       %% "pekko-cluster-typed"              % pekkoVersion // Not used in code, but loaded by reflection (ClusterReceptionist) at startup when pekko.actor.provider = cluster
}
