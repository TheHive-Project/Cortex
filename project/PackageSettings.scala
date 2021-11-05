import Common.{betaVersion, snapshotVersion, stableVersion, versionUsage}
import com.typesafe.sbt.SbtNativePackager.autoImport.{maintainer, maintainerScripts, packageDescription, packageSummary}
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging.autoImport.maintainerScriptsFromDirectory
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport.{debianPackageDependencies, debianPackageRecommends, Debian, DebianConstants}
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport.{
  defaultLinuxInstallLocation,
  linuxEtcDefaultTemplate,
  linuxMakeStartScript,
  linuxPackageMappings,
  packageMapping
}
import com.typesafe.sbt.packager.linux.Mapper.configWithNoReplace
import com.typesafe.sbt.packager.rpm.RpmPlugin.autoImport.{Rpm, RpmConstants}
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import sbt.Keys._
import sbt.Package.ManifestAttributes
import sbt._

import java.util.jar.Attributes.Name._

object PackageSettings {
  val rpmSettings = Seq(
    Rpm / version := {
      version.value match {
        case stableVersion(v1, _)                   => v1
        case betaVersion(v1, _, _)                  => v1
        case snapshotVersion(stableVersion(v1, _))  => v1
        case snapshotVersion(betaVersion(v1, _, _)) => v1
        case _                                      => versionUsage(version.value)
      }
    },
    rpmRelease := {
      version.value match {
        case stableVersion(_, v2)                    => v2
        case betaVersion(_, v2, v3)                  => "0." + v3 + "RC" + v2
        case snapshotVersion(stableVersion(_, v2))   => v2 + "-SNAPSHOT"
        case snapshotVersion(betaVersion(_, v2, v3)) => "0." + v3 + "RC" + v2 + "-SNAPSHOT"
        case _                                       => versionUsage(version.value)
      }
    },
    rpmVendor := organizationName.value,
    rpmUrl := organizationHomepage.value.map(_.toString),
    rpmLicense := Some("AGPL"),
    rpmRequirements += "java-1.8.0-openjdk-headless",
    Rpm / maintainerScripts := maintainerScriptsFromDirectory(
      baseDirectory.value / "package" / "rpm",
      Seq(RpmConstants.Pre, RpmConstants.Preun, RpmConstants.Post, RpmConstants.Postun)
    ),
    Rpm / linuxPackageSymlinks := Nil,
    rpmPrefix := Some(defaultLinuxInstallLocation.value),
    Rpm / linuxEtcDefaultTemplate := (baseDirectory.value / "package" / "etc_default_cortex").asURL,
    Rpm / linuxPackageMappings := configWithNoReplace((Rpm / linuxPackageMappings).value),
    Rpm / packageBin := {
      import scala.sys.process._
      val rpmFile = (Rpm / packageBin).value
      Process(
        "rpm" ::
          "--define" :: "_gpg_name TheHive Project" ::
          "--define" :: "_signature gpg" ::
          "--define" :: "__gpg_check_password_cmd /bin/true" ::
          "--define" :: "__gpg_sign_cmd %{__gpg} gpg --batch --no-verbose --no-armor --use-agent --no-secmem-warning -u \"%{_gpg_name}\" -sbo %{__signature_filename} %{__plaintext_filename}" ::
          "--addsign" :: rpmFile.toString ::
          Nil
      ).!!
      rpmFile
    }
  )

  val debianSettings = Seq(
    Debian / linuxPackageMappings += packageMapping(file("LICENSE") -> "/usr/share/doc/cortex/copyright").withPerms("644"),
    Debian / version := {
      version.value match {
        case stableVersion(_, _)                      => version.value
        case betaVersion(v1, v2, v3)                  => v1 + "-0." + v3 + "RC" + v2
        case snapshotVersion(stableVersion(v1, v2))   => v1 + "-" + v2 + "-SNAPSHOT"
        case snapshotVersion(betaVersion(v1, v2, v3)) => v1 + "-0." + v3 + "RC" + v2 + "-SNAPSHOT"
        case _                                        => versionUsage(version.value)
      }
    },
    debianPackageRecommends := Seq("elasticsearch"),
    debianPackageDependencies += "java8-runtime | java8-runtime-headless",
    Debian / maintainerScripts := maintainerScriptsFromDirectory(
      baseDirectory.value / "package" / "debian",
      Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
    ),
    Debian / linuxEtcDefaultTemplate := (baseDirectory.value / "package" / "etc_default_cortex").asURL,
    Debian / linuxMakeStartScript := None
  )

  val packageSettings = Seq(
    packageOptions ++= Seq(
      ManifestAttributes(IMPLEMENTATION_TITLE   -> name.value),
      ManifestAttributes(IMPLEMENTATION_VERSION -> version.value),
      ManifestAttributes(SPECIFICATION_VENDOR   -> "TheHive Project"),
      ManifestAttributes(SPECIFICATION_TITLE    -> name.value),
      ManifestAttributes(SPECIFICATION_VERSION  -> "TheHive Project")
    ),
    // Install files //
    Universal / mappings ~= {
      _.flatMap {
        case (_, "conf/application.conf")      => Nil
        case (file, "conf/apllication.sample") => Seq(file -> "conf/application.conf")
        case (_, "conf/logback.xml")           => Nil
        case other                             => Seq(other)
      } ++ Seq(
        file("package/cortex.service") -> "package/cortex.service",
        file("package/cortex.conf")    -> "package/cortex.conf",
        file("package/cortex")         -> "package/cortex",
        file("package/logback.xml")    -> "conf/logback.xml"
      )
    },
    maintainer := "TheHive Project <support@thehive-project.org>",
    packageSummary := "Powerful Observable Analysis Engine",
    packageDescription := """Cortex tries to solve a common problem frequently encountered by SOCs, CSIRTs and security
                            | researchers in the course of threat intelligence, digital forensics and incident response: how to analyze
                            | observables they have collected, at scale, by querying a single tool instead of several?
                            | Cortex, an open source and free software, has been created by TheHive Project for this very purpose. Observables,
                            | such as IP and email addresses, URLs, domain names, files or hashes, can be analyzed one by one or in bulk mode
                            | using a Web interface. Analysts can also automate these operations thanks to the Cortex REST API. """.stripMargin,
    defaultLinuxInstallLocation := "/opt",
    linuxPackageMappings ~= {
      _.map { pm =>
        val mappings = pm.mappings.filterNot {
          case (_, path) =>
            path.startsWith("/opt/cortex/package") || (path.startsWith("/opt/cortex/conf") && path != "/opt/cortex/conf/reference.conf")
        }
        com.typesafe.sbt.packager.linux.LinuxPackageMapping(mappings, pm.fileData).withConfig()
      } :+ packageMapping(
        file("package/cortex.service")  -> "/etc/systemd/system/cortex.service",
        file("package/cortex.conf")     -> "/etc/init/cortex.conf",
        file("package/cortex")          -> "/etc/init.d/cortex",
        file("conf/application.sample") -> "/etc/cortex/application.conf",
        file("package/logback.xml")     -> "/etc/cortex/logback.xml"
      ).withConfig()
    }
  )
}
