name := """cortex"""

lazy val cortex = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(PublishToBinTray.settings)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  cache,
  ws,
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

releaseVersionUIFile := baseDirectory.value / "ui" / "package.json"

changelogFile := baseDirectory.value / "CHANGELOG.md"

publishArtifact in (Compile, packageDoc) := false

publishArtifact in packageDoc := false

sources in (Compile,doc) := Seq.empty

// Front-end //
run := {
  (run in Compile).evaluated
  frontendDev.value
}

mappings in packageBin in Assets ++= frontendFiles.value

// Install files //

mappings in Universal ++= {
  val dir = baseDirectory.value / "install"
  (dir.***) pair relativeTo(dir.getParentFile)
}

// Release //
import Release._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

bintrayOrganization := Some("cert-bdf")

bintrayRepository := "cortex"

publish := {
  publishRelease.value
  publishLatest.value
}

releaseProcess := Seq[ReleaseStep](
  checkUncommittedChanges,
  checkSnapshotDependencies,
  getVersionFromBranch,
  runTest,
  releaseMerge,
  checkoutMaster,
  setReleaseVersion,
  setReleaseUIVersion,
  generateChangelog,
  commitChanges,
  tagRelease,
  publishArtifacts,
  checkoutDevelop,
  setNextVersion,
  setNextUIVersion,
  commitChanges,
  //commitNextVersion,
  pushChanges)


// DOCKER //

dockerBaseImage := "certbdf/thehive:latest"

packageName in Docker := "thehive-cortex"

dockerRepository := Some("certbdf")

dockerUpdateLatest := true

mappings in Universal += file("docker/entrypoint") -> "bin/entrypoint"

mappings in Universal ~= { _.filterNot {
  case (_, fileName) => fileName.startsWith("conf/") && name != "conf/keepme"
}}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

dockerCommands := dockerCommands.value.map {
  case ExecCmd("ENTRYPOINT", _*) => ExecCmd("ENTRYPOINT", "bin/entrypoint")
  case cmd                       => cmd
}

dockerCommands := dockerCommands.value.head +:
  Cmd("USER", "root") +:
  ExecCmd("RUN", "bash", "-c",
    "apt-get update && " +
    "apt-get install -y --no-install-recommends python-pip python2.7-dev ssdeep libfuzzy-dev libfuzzy2 libimage-exiftool-perl libmagic1 build-essential git && " +
    "cd /opt && " +
    "git clone https://github.com/CERT-BDF/Cortex-Analyzers.git && " +
    "pip install $(cat Cortex-Analyzers/analyzers/*/requirements.txt | grep -v hashlib | sort -u)") +:
  Cmd("EXPOSE", "9001") +:
  dockerCommands.value.tail

// Scalariform //
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._

ScalariformKeys.preferences in ThisBuild := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, false)
//  .setPreference(FirstParameterOnNewline, Force)
  .setPreference(AlignArguments, true)
//  .setPreference(FirstArgumentOnNewline, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 60)
  .setPreference(CompactControlReadability, true)
  .setPreference(CompactStringConcatenation, false)
  .setPreference(DoubleIndentClassDeclaration, true)
//  .setPreference(DoubleIndentMethodDeclaration, true)
  .setPreference(FormatXml, true)
  .setPreference(IndentLocalDefs, false)
  .setPreference(IndentPackageBlocks, false)
  .setPreference(IndentSpaces, 2)
  .setPreference(IndentWithTabs, false)
  .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
//  .setPreference(NewlineAtEndOfFile, true)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
  .setPreference(PreserveSpaceBeforeArguments, false)
//  .setPreference(PreserveDanglingCloseParenthesis, false)
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(SpaceBeforeColon, false)
//  .setPreference(SpaceBeforeContextColon, false)
  .setPreference(SpaceInsideBrackets, false)
  .setPreference(SpaceInsideParentheses, false)
  .setPreference(SpacesWithinPatternBinders, true)
  .setPreference(SpacesAroundMultiImports, true)
