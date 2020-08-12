import Common.{betaVersion, snapshotVersion, stableVersion}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

version in Docker := {
  version.value match {
    case stableVersion(_, _)   => version.value
    case betaVersion(v1, v2)   => v1 + "-0.1RC" + v2
    case snapshotVersion(_, _) => version.value + "-SNAPSHOT"
    case _                     => sys.error("Invalid version: " + version.value)
  }
}
defaultLinuxInstallLocation in Docker := "/opt/cortex"
dockerRepository := Some("thehiveproject")
dockerUpdateLatest := !version.value.toUpperCase.contains("RC") && !version.value.contains("SNAPSHOT")
dockerEntrypoint := Seq("/opt/cortex/entrypoint")
dockerExposedPorts := Seq(9001)
mappings in Docker ++= Seq(
  file("package/docker/entrypoint") -> "/opt/cortex/entrypoint",
  file("package/logback.xml")       -> "/etc/cortex/logback.xml",
  file("package/empty")             -> "/var/log/cortex/application.log"
)
mappings in Docker ~= (_.filterNot {
  case (_, filepath) => filepath == "/opt/cortex/conf/application.conf"
})
dockerCommands := Seq(
  Cmd("FROM", "openjdk:8"),
  Cmd("LABEL", "MAINTAINER=\"TheHive Project <support@thehive-project.org>\"", "repository=\"https://github.com/TheHive-Project/TheHive\""),
  Cmd("WORKDIR", "/opt/cortex"),
  // format: off
  Cmd("RUN",
    "apt", "update", "&&",
    "apt", "upgrade", "-y", "&&",
    "apt", "autoclean", "-y", "-q",  "&&",
    "apt", "autoremove", "-y", "-q",  "&&",
    "rm", "-rf", "/var/lib/apt/lists/*", "&&",
    "(", "type", "groupadd", "1>/dev/null", "2>&1", "&&",
      "groupadd", "-g", "1000", "cortex", "||",
      "addgroup", "-g", "1000", "-S", "cortex",
    ")", "&&",
    "(", "type", "useradd", "1>/dev/null", "2>&1", "&&",
      "useradd", "--system", "--uid", "1000", "--gid", "1000", "cortex", "||",
      "adduser", "-S", "-u", "1000", "-G", "cortex", "cortex",
    ")"),
  //format: on
  Cmd("ADD", "--chown=root:root", "opt", "/opt"),
  Cmd("ADD", "--chown=cortex:cortex", "var", "/var"),
  Cmd("ADD", "--chown=cortex:cortex", "etc", "/etc"),
  ExecCmd("RUN", "chmod", "+x", "/opt/cortex/bin/cortex", "/opt/cortex/entrypoint"),
  Cmd("EXPOSE", "9001"),
  Cmd("USER", "thehive"),
  ExecCmd("ENTRYPOINT", "/opt/cortex/entrypoint"),
  ExecCmd("CMD")
)
