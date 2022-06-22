import sbt._

object Dependencies {
  val scalaVersion = "2.12.16"

  object Play {
    val version = play.core.PlayVersion.current
    val ws      = "com.typesafe.play" %% "play-ws"         % version
    val ahc     = "com.typesafe.play" %% "play-ahc-ws"     % version
    val cache   = "com.typesafe.play" %% "play-ehcache"    % version
    val test    = "com.typesafe.play" %% "play-test"       % version
    val specs2  = "com.typesafe.play" %% "play-specs2"     % version
    val filters = "com.typesafe.play" %% "filters-helpers" % version
    val guice   = "com.typesafe.play" %% "play-guice"      % version
  }

  val scalaGuice = "net.codingwell" %% "scala-guice" % "5.1.0"

  val reflections      = "org.reflections"      % "reflections"        % "0.10.2"
  val zip4j            = "net.lingala.zip4j"    % "zip4j"              % "2.10.0"
  val elastic4play     = "org.thehive-project" %% "elastic4play"       % "1.13.6"
  val dockerClient     = "com.spotify"          % "docker-client"      % "8.14.4"
  val akkaCluster      = "com.typesafe.akka"   %% "akka-cluster"       % play.core.PlayVersion.akkaVersion
  val akkaClusterTyped = "com.typesafe.akka"   %% "akka-cluster-typed" % play.core.PlayVersion.akkaVersion
}
