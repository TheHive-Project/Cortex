import sbt.Keys._
import sbt._
import scala.sys.process.Process
import Path.rebase

object FrontEnd extends AutoPlugin {

  object autoImport {
    val frontendFiles = taskKey[Seq[(File, String)]]("Front-end files")
  }

  import autoImport._

  override def trigger = allRequirements

  override def projectSettings = Seq[Setting[_]](
    frontendFiles := {
      val s = streams.value
      s.log.info("Building front-end ...")
      s.log.info("npm install")
      Process("npm" :: "install" :: Nil, baseDirectory.value / "www") ! s.log
      s.log.info("npm run build")
      Process("npm" :: "run" :: "build" :: Nil, baseDirectory.value / "www") ! s.log
      val dir = baseDirectory.value / "www" / "dist"
      dir.**(AllPassFilter) pair rebase(dir, "www")
    })
}