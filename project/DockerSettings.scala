import Common.{betaVersion, snapshotVersion, stableVersion, versionUsage}
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport.defaultLinuxInstallLocation
import sbt.Keys._
import sbt._

object DockerSettings {

  def run(commands: String*): Cmd = Cmd("RUN", commands.map(_.trim).mkString(" && ").replaceAll("\\n", " && "))
  val init =
    """
      |apt update
      |apt upgrade -y
      |""".stripMargin
  val installJava =
    """
      |apt install -y curl gnupg
      |curl -fL https://apt.corretto.aws/corretto.key | gpg --dearmor -o /usr/share/keyrings/corretto.gpg
      |echo 'deb [signed-by=/usr/share/keyrings/corretto.gpg] https://apt.corretto.aws stable main' > /etc/apt/sources.list.d/corretto.list
      |apt update
      |apt install -y java-11-amazon-corretto-jdk
      |""".stripMargin
  val installDocker =
    """
      |curl -fsSL https://download.docker.com/linux/debian/gpg -o /usr/share/keyrings/docker.asc
      |echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
      |apt update
      |apt install -y docker-ce docker-ce-cli containerd.io docker-ce-rootless-extras uidmap iproute2 fuse-overlayfs
      |""".stripMargin
  val createDefaultUser =
    """
      |groupadd -g 1001 cortex
      |useradd --system --uid 1001 --gid 1001 --groups docker cortex -d /opt/cortex
      |mkdir -m 777 /var/log/cortex
      |""".stripMargin
  val setupDocker =
    """
      |chmod 666 /etc/subuid /etc/subgid
      |""".stripMargin
  val cleanup =
    """
      |rm -rf /var/lib/apt/lists/*
      |apt autoclean -y -q
      |apt autoremove -y -q
      |""".stripMargin

  val default = Seq(
    Docker / version := {
      version.value match {
        case stableVersion(_, _)                      => version.value
        case betaVersion(v1, v2, v3)                  => v1 + "-0." + v3 + "RC" + v2
        case snapshotVersion(stableVersion(v1, v2))   => v1 + "-" + v2 + "-SNAPSHOT"
        case snapshotVersion(betaVersion(v1, v2, v3)) => v1 + "-0." + v3 + "RC" + v2 + "-SNAPSHOT"
        case _                                        => versionUsage(version.value)
      }
    },
    dockerGroupLayers := PartialFunction.empty,
    Docker / defaultLinuxInstallLocation := "/opt/cortex",
    dockerRepository := Some("thehiveproject"),
    dockerUpdateLatest := !version.value.toUpperCase.contains("RC") && !version.value.contains("SNAPSHOT"),
    dockerExposedPorts := Seq(9001),
    Docker / mappings ++= Seq(
      file("package/docker/entrypoint") -> "/opt/cortex/entrypoint",
      file("package/logback.xml")       -> "/etc/cortex/logback.xml"
    ),
    Docker / mappings ~= (_.filterNot {
      case (_, filepath) => filepath == "/opt/cortex/conf/application.conf" || filepath.contains("/package/")
    }),
    dockerCommands := Seq(
      Cmd("FROM", "debian:bookworm-slim"),
      Cmd("LABEL", "MAINTAINER=\"TheHive Project <support@thehive-project.org>\"", "repository=\"https://github.com/TheHive-Project/TheHive\""),
      Cmd("WORKDIR", "/opt/cortex"),
      Cmd("ENV", "JAVA_HOME", "/usr/lib/jvm/java-11-amazon-corretto"),
      run(init,
      installJava,
      installDocker,
      createDefaultUser,
      setupDocker,
      cleanup),
      Cmd("ADD", "--chown=root:root", "opt", "/opt"),
      Cmd("ADD", "--chown=cortex:cortex", "--chmod=755", "etc", "/etc"),
      Cmd("VOLUME", "/var/lib/docker"),
      ExecCmd("RUN", "chmod", "+x", "/opt/cortex/bin/cortex", "/opt/cortex/entrypoint"),
      Cmd("EXPOSE", "9001"),
      ExecCmd("ENTRYPOINT", "/opt/cortex/entrypoint"),
      ExecCmd("CMD")
    )
  )

  val withDeps = default ++ Seq(
    dockerCommands ++= Seq(
      Cmd(
        "RUN",
        """
    | apt update &&
    | apt upgrade -y &&
    | apt install -y -q --no-install-recommends --no-install-suggests
    |   wkhtmltopdf libfuzzy-dev libimage-exiftool-perl curl unzip
    |   libboost-regex-dev
    |   libboost-program-options-dev
    |   libboost-system-dev libboost-filesystem-dev libssl-dev
    |   build-essential cmake python3-dev python2-dev
    |   git python3 python3-pip libffi-dev libjpeg62-turbo-dev libtiff5-dev
    |   libopenjp2-7-dev zlib1g-dev libfreetype6-dev liblcms2-dev libwebp-dev
    |   tcl8.6-dev tk8.6-dev python3-tk libharfbuzz-dev libfribidi-dev
    |   libxcb1-dev python2.7 &&
    | rm -rf /var/lib/apt/lists/* &&
    | curl https://bootstrap.pypa.io/pip/2.7/get-pip.py --output /tmp/get-pip.py &&
    | python2.7 /tmp/get-pip.py &&
    | pip2 install -U setuptools &&
    | pip3 install -U setuptools &&
    | ln -sf python3 /usr/bin/python &&
    | hash -r &&
    | git clone https://github.com/JusticeRage/Manalyze.git /tmp/Manalyze &&
    | cd /tmp/Manalyze &&
    | cmake . &&
    | make -j5 &&
    | cd /tmp/Manalyze/bin/yara_rules &&
    | pip3 install requests &&
    | python3 update_clamav_signatures.py &&
    | cd /tmp/Manalyze &&
    | make install &&
    | cd / &&
    | rm -rf /tmp/Manalyze &&
    | curl -SL https://github.com/fireeye/flare-floss/releases/download/v1.7.0/floss-v1.7.0-linux.zip
    |   --output /tmp/floss.zip &&
    | unzip /tmp/floss.zip -d /usr/bin &&
    | rm /tmp/floss.zip &&
    | git clone https://github.com/TheHive-Project/Cortex-Analyzers.git /tmp/analyzers &&
    | cat $(find /tmp/analyzers -name requirements.txt) | sort -u | while read I ;
    | do
    |   pip2 install $I || true &&
    |   pip3 install $I || true ;
    | done &&
    | for I in $(find /tmp/analyzers -name requirements.txt) ;
    | do
    |   pip2 install -r $I || true &&
    |   pip3 install -r $I || true ;
    | done &&
    | rm -rf /tmp/analyzers
        """.stripMargin.split("\\s").filter(_.nonEmpty): _*
      )
    )
  )
}
