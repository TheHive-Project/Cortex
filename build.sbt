import Common.*
import Dependencies.{nettyVersion, pekkoVersion}

ThisBuild / scalaVersion := Dependencies.scalaVersion
ThisBuild / evictionErrorLevel := util.Level.Warn

ThisBuild / dependencyOverrides ++= Seq(
  Dependencies.Play.twirl,
  "com.fasterxml.jackson.core" % "jackson-databind"             % "2.19.2",
  "org.apache.commons"         % "commons-compress"             % "1.28.0",
  "com.google.guava"           % "guava"                        % "32.1.1-jre",
  "org.apache.pekko"           %% "pekko-actor"                 % pekkoVersion,
  "org.apache.pekko"           %% "pekko-serialization-jackson" % pekkoVersion,
  "org.apache.pekko"           %% "pekko-actor-typed"           % pekkoVersion,
  "org.apache.pekko"           %% "pekko-slf4j"                 % pekkoVersion,
  "ch.qos.logback"             % "logback-core"                 % "1.5.20",
  "io.netty"                   % "netty-handler-proxy"          % nettyVersion,
  "io.netty"                   % "netty-resolver-dns"           % nettyVersion,
  "io.netty"                   % "netty-codec-http"             % nettyVersion,
  "io.netty"                   % "netty-codec-http2"            % nettyVersion
)
lazy val cortex = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(elastic4play)
  .settings(projectSettings)
  .settings(PackageSettings.packageSettings)
  .settings(PackageSettings.rpmSettings)
  .settings(PackageSettings.debianSettings)
  .settings(DockerSettings.default)
  .settings(
    Seq(
      libraryDependencies ++= Seq(
        caffeine,
        Dependencies.Play.ws,
        Dependencies.Play.ahc,
        Dependencies.Play.specs2 % Test,
        Dependencies.Play.guice,
        Dependencies.scalaGuice,
        Dependencies.reflections,
        Dependencies.zip4j,
        Dependencies.dockerJavaClient,
        Dependencies.dockerJavaTransport,
        Dependencies.k8sClient,
        Dependencies.pekkoCluster,
        Dependencies.pekkoClusterTyped
      ),
      dependencyOverrides ++= Seq(
        "com.github.jnr" % "jffi"           % "1.3.11",
        "com.github.jnr" % "jnr-ffi"        % "2.2.13",
        "com.github.jnr" % "jnr-enxio"      % "0.32.14",
        "com.github.jnr" % "jnr-unixsocket" % "0.38.19"
      ),
      Compile / packageDoc / publishArtifact := false,
      Compile / doc / sources := Seq.empty,
      // Front-end //
      Assets / packageBin / mappings ++= frontendFiles.value,
      packageBin := {
        (Debian / packageBin).value
        (Rpm / packageBin).value
        (Universal / packageBin).value
      }
    )
  )

val elastic4sVersion = "8.19.0"

lazy val elastic4play = (project in file("elastic4play"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      cacheApi,
      "nl.gn0s1s"        %% "elastic4s-core" % elastic4sVersion,
      "nl.gn0s1s"        %% "elastic4s-reactivestreams-pekko" % elastic4sVersion,
      "nl.gn0s1s"        %% "elastic4s-client-esjava" % elastic4sVersion,
      "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
      "org.scalactic"    %% "scalactic" % "3.2.19",
      specs2             % Test
    )
  )

lazy val cortexWithDeps = (project in file("target/docker-withdeps"))
  .dependsOn(cortex)
  .enablePlugins(DockerPlugin)
  .settings(projectSettings)
  .settings(DockerSettings.withDeps)
  .settings(
    Docker / mappings := (cortex / Docker / mappings).value,
    Docker / version := version.value + "-withdeps",
    Docker / packageName := "cortex"
  )
