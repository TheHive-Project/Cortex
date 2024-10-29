// Comment to get more information during initialization
logLevel := Level.Info

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play"   % "sbt-plugin"           % "2.8.16")
addSbtPlugin("org.scalameta"       % "sbt-scalafmt"         % "2.4.6")
addSbtPlugin("org.thehive-project" % "sbt-github-changelog" % "0.4.0")
addSbtPlugin("org.xerial.sbt"      % "sbt-sonatype"         % "3.9.10")
