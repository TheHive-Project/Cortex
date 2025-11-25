import sbt.*

object Dependencies {
  val scalaVersion      = "2.13.17"
  val dockerJavaVersion = "3.6.0"
  val pekkoVersion      = "1.2.1"
  val nettyVersion      = "4.1.128.Final"

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
  val dockerJavaClient    = "com.github.docker-java" % "docker-java-core"              % dockerJavaVersion
  val dockerJavaTransport = "com.github.docker-java" % "docker-java-transport-zerodep" % dockerJavaVersion
  val k8sClient           = "io.fabric8"             % "kubernetes-client"             % "7.4.0"
  val pekkoCluster        = "org.apache.pekko"       %% "pekko-cluster"                % pekkoVersion
  val pekkoClusterTyped   = "org.apache.pekko"       %% "pekko-cluster-typed"          % pekkoVersion
}
