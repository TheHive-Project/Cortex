import Common.{betaVersion, snapshotVersion, stableVersion}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

version in Docker := {
  version.value match {
    case stableVersion(_, _) => version.value
    case betaVersion(v1, v2) => v1 + "-0.1RC" + v2
    case snapshotVersion(_, _) => version.value + "-SNAPSHOT"
    case _ => sys.error("Invalid version: " + version.value)
  }
}
defaultLinuxInstallLocation in Docker := "/opt/cortex"
dockerRepository := Some("thehiveproject")
dockerUpdateLatest := !version.value.toUpperCase.contains("RC")
dockerEntrypoint := Seq("/opt/cortex/entrypoint")
dockerExposedPorts := Seq(9000)
mappings in Docker ++= Seq(
  file("package/docker/entrypoint") -> "/opt/cortex/entrypoint",
  file("package/logback.xml") -> "/etc/cortex/logback.xml",
  file("package/empty") -> "/var/log/cortex/application.log")
mappings in Docker ~= (_.filterNot {
  case (_, filepath) => filepath == "/opt/cortex/conf/application.conf"
})
dockerCommands ~= { dc =>
  val (dockerInitCmds, dockerTailCmds) = dc
    .collect {
      case ExecCmd("RUN", "chown", _*) => ExecCmd("RUN", "chown", "-R", "daemon:root", ".")
      case other => other
    }
    .splitAt(4)
  dockerInitCmds ++
    Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN", "bash", "-c",
        "apt-get update && " +
          "apt-get install -y --no-install-recommends python-pip python2.7-dev python3-pip python3-dev ssdeep libfuzzy-dev libfuzzy2 libimage-exiftool-perl libmagic1 build-essential git libssl-dev dnsutils && " +
          "pip2 install -U pip setuptools && " +
          "pip3 install -U pip setuptools && " +
          "hash -r && " +
          "cd /opt && " +
          "git clone https://github.com/TheHive-Project/Cortex-Analyzers.git && " +
          "for I in $(find Cortex-Analyzers -name 'requirements.txt'); do pip2 install -r $I; done && " +
          "for I in $(find Cortex-Analyzers -name 'requirements.txt'); do pip3 install -r $I || true; done"),
      Cmd("ADD", "var", "/var"),
      Cmd("ADD", "etc", "/etc"),
      ExecCmd("RUN", "chown", "-R", "daemon:root", "/var/log/cortex"),
      ExecCmd("RUN", "chmod", "+x", "/opt/cortex/bin/cortex", "/opt/cortex/entrypoint")) ++
    dockerTailCmds
}
