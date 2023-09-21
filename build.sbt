import Common._

ThisBuild / scalaVersion := Dependencies.scalaVersion
ThisBuild / evictionErrorLevel := util.Level.Warn

ThisBuild / dependencyOverrides ++= Seq(
  Dependencies.Play.twirl,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.13.5",
  "org.apache.commons"         % "commons-compress" % "1.23.0",
  "com.google.guava"           % "guava"            % "32.1.1-jre"
)
lazy val cortex = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(projectSettings)
  .settings(PackageSettings.packageSettings)
  .settings(PackageSettings.rpmSettings)
  .settings(PackageSettings.debianSettings)
  .settings(DockerSettings.default)
  .settings(
    Seq(
      libraryDependencies ++= Seq(
        Dependencies.Play.cache,
        Dependencies.Play.ws,
        Dependencies.Play.ahc,
        Dependencies.Play.specs2 % Test,
        Dependencies.Play.guice,
        Dependencies.scalaGuice,
        Dependencies.elastic4play,
        Dependencies.reflections,
        Dependencies.zip4j,
        Dependencies.dockerClient,
        Dependencies.akkaCluster,
        Dependencies.akkaClusterTyped
      ),
      dependencyOverrides ++= Seq(
        "com.github.jnr" % "jffi" % "1.3.11",
        "com.github.jnr" % "jnr-ffi" % "2.2.13",
        "com.github.jnr" % "jnr-enxio" % "0.32.14",
        "com.github.jnr" % "jnr-unixsocket" % "0.38.19"
      ),
      resolvers += Resolver.sbtPluginRepo("releases"),
      resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
      resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven",
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
