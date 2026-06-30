import Common.*
import Dependencies.{bouncyCastleVersion, jacksonVersion, nettyVersion, pekkoVersion, vertxVersion}

ThisBuild / scalaVersion := Dependencies.scalaVersion
ThisBuild / evictionErrorLevel := util.Level.Warn

// CVE-2025-12183 / CVE-2025-66566: the org.lz4:lz4-java project was archived and
// fixes ship under at.yawk.lz4:lz4-java (added below in elastic4play's deps).
// Strip the vulnerable transitive everywhere to avoid two copies on the classpath.
ThisBuild / excludeDependencies += ExclusionRule("org.lz4", "lz4-java")

ThisBuild / dependencyOverrides ++= Seq(
  // jackson-module-scala 2.21.x declares scala-library 2.13.18; keep it pinned to our
  // compiler version to avoid the SIP-51 "scala-library newer than compiler" build error.
  "org.scala-lang" % "scala-library" % Dependencies.scalaVersion,
  Dependencies.Play.twirl,
  // CVE-2026-54512 / CVE-2026-54513: PolymorphicTypeValidator bypass via generic type
  // parameters, fixed in 2.21.4. Pulled in (as a family) via pekko-serialization-jackson.
  // Bumped to 2.22.0 (latest) to stay ahead of the 2.x advisory line.
  // Keep the whole jackson family aligned to avoid mixed-version deserialization issues.
  "com.fasterxml.jackson.core"       % "jackson-databind"               % jacksonVersion,
  "com.fasterxml.jackson.core"       % "jackson-core"                   % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor"        % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"        % jacksonVersion,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-jdk8"          % jacksonVersion,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-jsr310"        % jacksonVersion,
  "com.fasterxml.jackson.module"     % "jackson-module-parameter-names" % jacksonVersion,
  "com.fasterxml.jackson.module"     %% "jackson-module-scala"          % jacksonVersion,
  "org.apache.commons"               % "commons-compress"               % "1.28.0",
  "com.google.guava"                 % "guava"                          % "32.1.1-jre",
  "org.apache.pekko"                 %% "pekko-actor"                   % pekkoVersion,
  "org.apache.pekko"                 %% "pekko-serialization-jackson"   % pekkoVersion,
  "org.apache.pekko"                 %% "pekko-actor-typed"             % pekkoVersion,
  "org.apache.pekko"                 %% "pekko-slf4j"                   % pekkoVersion,
  "ch.qos.logback"                   % "logback-core"                   % "1.5.32",
  "ch.qos.logback"                   % "logback-classic"                % "1.5.32",
  // 4.1.135.Final fixes a batch of 2026 netty CVEs across codec-http (CVE-2026-42587,
  // CVE-2026-50020), codec-http2 (CVE-2026-48043, CVE-2026-50560, CVE-2026-47244),
  // handler (CVE-2026-44249, CVE-2026-50010, CVE-2026-45416) and resolver-dns
  // (CVE-2026-45674, CVE-2026-47691, CVE-2026-45673). Keep the whole family aligned.
  "io.netty" % "netty-buffer"                       % nettyVersion,
  "io.netty" % "netty-common"                       % nettyVersion,
  "io.netty" % "netty-transport"                    % nettyVersion,
  "io.netty" % "netty-transport-native-unix-common" % nettyVersion,
  "io.netty" % "netty-resolver"                     % nettyVersion,
  "io.netty" % "netty-resolver-dns"                 % nettyVersion,
  "io.netty" % "netty-codec"                        % nettyVersion,
  "io.netty" % "netty-codec-dns"                    % nettyVersion,
  "io.netty" % "netty-codec-socks"                  % nettyVersion,
  "io.netty" % "netty-codec-http"                   % nettyVersion,
  "io.netty" % "netty-codec-http2"                  % nettyVersion,
  "io.netty" % "netty-handler"                      % nettyVersion,
  "io.netty" % "netty-handler-proxy"                % nettyVersion,
  // CVE-2026-5588: bcpkix <= 1.83 accepts empty composite signature sequences. Pulled in via docker-java-core.
  "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleVersion,
  "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleVersion,
  "org.bouncycastle" % "bcutil-jdk18on" % bouncyCastleVersion,
  // CVE-2025-49574 (fixed in 4.5.16) and CVE-2026-1002 (fixed in 4.5.24). Pulled in via io.fabric8:kubernetes-client.
  "io.vertx" % "vertx-core"        % vertxVersion,
  "io.vertx" % "vertx-auth-common" % vertxVersion,
  "io.vertx" % "vertx-web-client"  % vertxVersion,
  "io.vertx" % "vertx-web-common"  % vertxVersion
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
      "nl.gn0s1s"        %% "elastic4s-core"                  % elastic4sVersion,
      "nl.gn0s1s"        %% "elastic4s-reactivestreams-pekko" % elastic4sVersion,
      "nl.gn0s1s"        %% "elastic4s-client-esjava"         % elastic4sVersion,
      "org.apache.pekko" %% "pekko-stream-testkit"            % pekkoVersion % Test,
      "org.scalactic"    %% "scalactic"                       % "3.2.19",
      Dependencies.lz4Java,
      specs2 % Test
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
