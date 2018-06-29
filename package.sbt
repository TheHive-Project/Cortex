// Add information in manifest
import Package.ManifestAttributes
import java.util.jar.Attributes.Name._
packageOptions  ++= Seq(
  ManifestAttributes(IMPLEMENTATION_TITLE -> name.value),
  ManifestAttributes(IMPLEMENTATION_VERSION -> version.value),
  ManifestAttributes(SPECIFICATION_VENDOR -> "TheHive Project"),
  ManifestAttributes(SPECIFICATION_TITLE -> name.value),
  ManifestAttributes(SPECIFICATION_VERSION -> "TheHive Project")
)

// Install files //
mappings in Universal ~= {
  _.flatMap {
    case (_, "conf/application.conf") => Nil
    case (file, "conf/apllication.sample") => Seq(file -> "conf/application.conf")
    case (_, "conf/logback.xml") => Nil
    case other => Seq(other)
  } ++ Seq(
    file("package/cortex.service") -> "package/cortex.service",
    file("package/cortex.conf") -> "package/cortex.conf",
    file("package/cortex") -> "package/cortex",
    file("package/logback.xml") -> "conf/logback.xml"
  )
}

maintainer := "TheHive Project <support@thehive-project.org>"
packageSummary := "Powerful Observable Analysis Engine"
packageDescription := """Cortex tries to solve a common problem frequently encountered by SOCs, CSIRTs and security
  | researchers in the course of threat intelligence, digital forensics and incident response: how to analyze
  | observables they have collected, at scale, by querying a single tool instead of several?
  | Cortex, an open source and free software, has been created by TheHive Project for this very purpose. Observables,
  | such as IP and email addresses, URLs, domain names, files or hashes, can be analyzed one by one or in bulk mode
  | using a Web interface. Analysts can also automate these operations thanks to the Cortex REST API. """.stripMargin
defaultLinuxInstallLocation := "/opt"
linuxPackageMappings ~= { _.map { pm =>
  val mappings = pm.mappings.filterNot {
    case (_, path) => path.startsWith("/opt/cortex/package") || (path.startsWith("/opt/cortex/conf") && path != "/opt/cortex/conf/reference.conf")
  }
  com.typesafe.sbt.packager.linux.LinuxPackageMapping(mappings, pm.fileData).withConfig()
} :+ packageMapping(
  file("package/cortex.service") -> "/etc/systemd/system/cortex.service",
  file("package/cortex.conf") -> "/etc/init/cortex.conf",
  file("package/cortex") -> "/etc/init.d/cortex",
  file("conf/application.sample") -> "/etc/cortex/application.conf",
  file("package/logback.xml") -> "/etc/cortex/logback.xml"
).withConfig()
}

packageBin := {
  (packageBin in Debian).value
  (packageBin in Rpm).value
  (packageBin in Universal).value
}
