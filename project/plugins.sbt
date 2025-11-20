// Comment to get more information during initialization
logLevel := Level.Info
evictionErrorLevel := util.Level.Warn

lazy val isEnabled: Set[String] = sys.props.getOrElse("plugins", "").split(",").map(_.trim).toSet

def maybeEnable(pair: (String, ModuleID)): Seq[Setting[?]] =
  if (isEnabled(pair._1)) addSbtPlugin(pair._2) else Seq()

Seq[(String, ModuleID)](
  "sbom"     -> "com.github.sbt" %% "sbt-sbom"             % "0.5.0",
  "depcheck" -> "net.nmoncho"     % "sbt-dependency-check" % "1.8.3"
).flatMap(maybeEnable)

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin"           % "2.9.8")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"         % "2.5.5")
