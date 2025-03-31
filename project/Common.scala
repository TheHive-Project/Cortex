import scala.util.matching.Regex

import sbt.Keys._
import sbt._

object Common {

  val projectSettings = Seq(
    organizationName := "TheHive-Project",
    organization := "org.thehive-project",
    licenses += "AGPL-V3" -> url("https://www.gnu.org/licenses/agpl-3.0.html"),
    organizationHomepage := Some(url("http://thehive-project.org/")),
    resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven",
    scalaVersion := Dependencies.scalaVersion,
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-feature",     // Emit warning and location for usages of features that should be imported explicitly.
      "-unchecked",   // Enable additional warnings where generated code depends on assumptions.
      //"-Xfatal-warnings", // Fail the compilation if there are any warnings.
      "-Xlint",                  // Enable recommended additional warnings.
      "-Ywarn-dead-code",        // Warn when dead code is identified.
      "-Ywarn-numeric-widen"     // Warn when numerics are widened.
    ),
    Test / scalacOptions ~= { options =>
      options filterNot (_ == "-Ywarn-dead-code") // Allow dead code in tests (to support using mockito).
    },
    Test / parallelExecution := false,
    Test / fork := true,
    javaOptions += "-Xmx1G",
    // Redirect logs from ElasticSearch (which uses log4j2) to slf4j
    libraryDependencies += "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.17.0",
    excludeDependencies += "org.apache.logging.log4j" % "log4j-core",
    dependencyOverrides += "com.typesafe.akka"        %% "akka-actor" % play.core.PlayVersion.akkaVersion
  )

  val stableVersion: Regex = "(\\d+\\.\\d+\\.\\d+)-(\\d+)".r
  val betaVersion: Regex   = "(\\d+\\.\\d+\\.\\d+)-[Rr][Cc](\\d+)-(\\d+)".r

  object snapshotVersion {

    def unapply(version: String): Option[String] =
      if (version.endsWith("-SNAPSHOT")) Some(version.dropRight(9))
      else None
  }

  def versionUsage(version: String): Nothing =
    sys.error(
      s"Invalid version: $version\n" +
        "The accepted formats for version are:\n" +
        " - 1.2.3-4\n" +
        " - 1.2.3-RC4-5\n" +
        " - 1.2.3-4-SNAPSHOT\n" +
        " - 1.2.3-RC4-5-SNAPSHOT"
    )
}
