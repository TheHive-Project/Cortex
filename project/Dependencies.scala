import sbt._

object Dependencies {
  val scalaVersion      = "2.13.15"
  val dockerJavaVersion = "3.4.0"

  object Play {
    val version = play.core.PlayVersion.current
    val ws      = "com.typesafe.play" %% "play-ws" % version exclude ("com.typesafe.play", "play-ws-standalone-xml")
    val ahc     = "com.typesafe.play" %% "play-ahc-ws" % version
    val cache   = "com.typesafe.play" %% "play-ehcache" % version
    val test    = "com.typesafe.play" %% "play-test" % version
    val specs2  = "com.typesafe.play" %% "play-specs2" % version
    val filters = "com.typesafe.play" %% "filters-helpers" % version
    val guice   = "com.typesafe.play" %% "play-guice" % version
    val twirl   = "com.typesafe.play" %% "twirl-api" % "1.6.8"
  }

  val scalaGuice = "net.codingwell" %% "scala-guice" % "7.0.0"

  val reflections         = "org.reflections"        % "reflections"                   % "0.10.2"
  val zip4j               = "net.lingala.zip4j"      % "zip4j"                         % "2.11.5"
  val dockerJavaClient    = "com.github.docker-java" % "docker-java"                   % dockerJavaVersion
  val dockerJavaTransport = "com.github.docker-java" % "docker-java-transport-zerodep" % dockerJavaVersion
  val akkaCluster         = "com.typesafe.akka"      %% "akka-cluster"                 % play.core.PlayVersion.akkaVersion
  val akkaClusterTyped    = "com.typesafe.akka"      %% "akka-cluster-typed"           % play.core.PlayVersion.akkaVersion
}
