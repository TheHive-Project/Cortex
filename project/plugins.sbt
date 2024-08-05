// Comment to get more information during initialization
logLevel := Level.Info
evictionErrorLevel := util.Level.Warn

// The Play plugin
addSbtPlugin("com.typesafe.play"   % "sbt-plugin"           % "2.8.19")
addSbtPlugin("org.scalameta"       % "sbt-scalafmt"         % "2.4.6")
addSbtPlugin("org.thehive-project" % "sbt-github-changelog" % "0.4.0")
addSbtPlugin("io.github.siculo"    %% "sbt-bom"             % "0.3.0")
addSbtPlugin("io.gatling"          % "gatling-sbt"          % "4.4.0")
