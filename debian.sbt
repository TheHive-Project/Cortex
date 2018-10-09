import Common._

version in Debian := getVersion(version.value) + '-' + getRelease(version.value)
debianPackageDependencies += "java8-runtime | java8-runtime-headless"
maintainerScripts in Debian := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "debian",
  Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
)
linuxEtcDefaultTemplate in Debian := (baseDirectory.value / "package" / "etc_default_cortex").asURL
linuxMakeStartScript in Debian := None
