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
dockerUpdateLatest := !version.value.toUpperCase.contains("RC") && !version.value.contains("SNAPSHOT")
dockerEntrypoint := Seq("/opt/cortex/entrypoint")
dockerExposedPorts := Seq(9001)
mappings in Docker ++= Seq(
  file("package/docker/entrypoint") -> "/opt/cortex/entrypoint",
  file("package/logback.xml") -> "/etc/cortex/logback.xml",
  file("package/empty") -> "/var/log/cortex/application.log")
mappings in Docker ~= (_.filterNot {
  case (_, filepath) => filepath == "/opt/cortex/conf/application.conf"
})
dockerCommands ~= { dc =>
  val (dockerInitCmds, dockerTailCmds) = dc
    .flatMap {
      case ExecCmd("RUN", "chown", _*) => Some(ExecCmd("RUN", "chown", "-R", "daemon:root", "."))
      case Cmd("USER", _) => None
      case other => Some(other)
    }
    .splitAt(4)
  dockerInitCmds ++
    Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN", "bash", "-c",
        "wget -q -O - https://download.docker.com/linux/static/stable/x86_64/docker-18.09.0.tgz | " +
          "tar -xzC /usr/local/bin/ --strip-components 1 && " +
          "addgroup --system dockremap && " +
          "adduser --system --ingroup dockremap dockremap && " +
          "addgroup --system docker && " +
          "usermod --append --groups docker daemon &&" +
          "echo 'dockremap:165536:65536' >> /etc/subuid && " +
          "echo 'dockremap:165536:65536' >> /etc/subgid && " +
          "apt-get update && " +
          "apt-get install -y --no-install-recommends python-pip python2.7-dev python3-pip python3-dev ssdeep libfuzzy-dev libfuzzy2 libimage-exiftool-perl libmagic1 build-essential git libssl-dev dnsutils iptables && " +
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
