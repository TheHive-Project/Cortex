import Common.*
import Dependencies.{bouncyCastleVersion, jacksonScalaVersion, jacksonVersion, nettyVersion, pekkoVersion, vertxVersion}

ThisBuild / scalaVersion := Dependencies.scalaVersion

// CVE-2025-12183 / CVE-2025-66566: the org.lz4:lz4-java project was archived and
// fixes ship under at.yawk.lz4:lz4-java (brought by Play).
// Strip the vulnerable transitive everywhere to avoid two copies on the classpath.
ThisBuild / excludeDependencies += ExclusionRule("org.lz4", "lz4-java")

// jcl-over-slf4j (brought by Play) re-implements the org.apache.commons.logging API on top of slf4j/logback.
// commons-logging (pulled by the ES rest client and httpclient) ships the same classes: drop it so only one
// copy is on the classpath, instead of mixing classes from both jars depending on classpath order.
ThisBuild / excludeDependencies += ExclusionRule("commons-logging", "commons-logging")

ThisBuild / dependencyOverrides ++= Seq(
  // Some deps (e.g. jackson-module-scala) may declare a scala-library newer than our compiler;
  // keep it pinned to avoid the SIP-51 "scala-library newer than compiler" build error.
  "org.scala-lang" % "scala-library" % Dependencies.scalaVersion,
  // CVE-2026-54512 / CVE-2026-54513: PolymorphicTypeValidator bypass via generic type
  // parameters, fixed in 2.21.4. Pulled in (as a family) via pekko-serialization-jackson.
  // 2.22.3 also fixes CVE-2026-89425 / CVE-2026-89407 (core) and CVE-2026-91777 / CVE-2026-91776 /
  // CVE-2026-68497 / CVE-2026-83557 / CVE-2026-19032 / CVE-2026-59889 / CVE-2026-54515 / CVE-2026-77310 (databind).
  // Keep the whole jackson family aligned to avoid mixed-version deserialization issues.
  "com.fasterxml.jackson.core"       % "jackson-databind"               % jacksonVersion,
  "com.fasterxml.jackson.core"       % "jackson-core"                   % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor"        % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"        % jacksonVersion,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-jdk8"          % jacksonVersion,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-jsr310"        % jacksonVersion,
  "com.fasterxml.jackson.module"     % "jackson-module-parameter-names" % jacksonVersion,
  "com.fasterxml.jackson.module"     %% "jackson-module-scala"          % jacksonScalaVersion,
  "org.apache.commons"               % "commons-compress"               % "1.28.0",
  "org.apache.pekko"                 %% "pekko-actor"                   % pekkoVersion,
  "org.apache.pekko"                 %% "pekko-serialization-jackson"   % pekkoVersion,
  "org.apache.pekko"                 %% "pekko-actor-typed"             % pekkoVersion,
  "org.apache.pekko"                 %% "pekko-slf4j"                   % pekkoVersion,
  "ch.qos.logback"                   % "logback-core"                   % "1.6.5",
  "ch.qos.logback"                   % "logback-classic"                % "1.6.5",
  // Keep the whole netty family aligned.
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
  // Pulled in via docker-java-core.
  "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleVersion,
  "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleVersion,
  "org.bouncycastle" % "bcutil-jdk18on" % bouncyCastleVersion,
  // Pulled in via io.fabric8:kubernetes-client.
  "io.vertx" % "vertx-core"        % vertxVersion,
  "io.vertx" % "vertx-auth-common" % vertxVersion,
  "io.vertx" % "vertx-web-client"  % vertxVersion,
  "io.vertx" % "vertx-web-common"  % vertxVersion,
  // Pulled in via jnr-ffi.
  "org.ow2.asm"                       % "asm"          % "9.11",
  "org.ow2.asm"                       % "asm-analysis" % "9.11",
  "org.ow2.asm"                       % "asm-commons"  % "9.11",
  "org.ow2.asm"                       % "asm-tree"     % "9.11",
  "org.ow2.asm"                       % "asm-util"     % "9.11",
  "org.apache.httpcomponents.client5" % "httpclient5"  % "5.6.5"
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
      // jnr is only used by lmdbjava (pekko-distributed-data durable store, not configured in Cortex).
      // Stay on the 2.2.x line targeted by lmdbjava.
      dependencyOverrides ++= Seq(
        "com.github.jnr" % "jffi"    % "1.3.15",
        "com.github.jnr" % "jnr-ffi" % "2.2.19"
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

val elastic4sVersion = "8.19.0" // 8.19.1 requires Pekko 1.4, 8.19.2 requires pekko 1.7.

lazy val elastic4play = (project in file("elastic4play"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      cacheApi,
      "nl.gn0s1s"        %% "elastic4s-core" % elastic4sVersion,
      "nl.gn0s1s"        %% "elastic4s-reactivestreams-pekko" % elastic4sVersion,
      "nl.gn0s1s"        %% "elastic4s-client-esjava" % elastic4sVersion,
      "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
      "org.scalactic"    %% "scalactic" % "3.2.20",
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
