import sbt.*

object Dependencies {
  val scalaVersion      = "2.12.16"
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
    val twirl   = "com.typesafe.play" %% "twirl-api" % "1.5.2"
  }

  val scalaGuice = "net.codingwell" %% "scala-guice" % "5.1.1"

  val reflections         = "org.reflections"        % "reflections"                   % "0.10.2"
  val zip4j               = "net.lingala.zip4j"      % "zip4j"                         % "2.11.5"
  val elastic4play        = "org.thehive-project"    %% "elastic4play"                 % "1.13.6"
  val dockerClient        = "com.spotify"            % "docker-client"                 % "8.16.0"
  val dockerJavaClient    = "com.github.docker-java" % "docker-java"                   % dockerJavaVersion
  val dockerJavaTransport = "com.github.docker-java" % "docker-java-transport-zerodep" % dockerJavaVersion
  val akkaCluster         = "com.typesafe.akka"      %% "akka-cluster"                 % play.core.PlayVersion.akkaVersion
  val akkaClusterTyped    = "com.typesafe.akka"      %% "akka-cluster-typed"           % play.core.PlayVersion.akkaVersion
}
