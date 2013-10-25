package sbtandroid

import AndroidPlugin.adbTarget
import AndroidPlugin.dbPath
import AndroidPlugin.install
import AndroidPlugin.manifestPackage
import AndroidPlugin.manifestPath
import AndroidPlugin.manifestSchema
import AndroidPlugin.start
import sbt.Keys.streams
import sbt.Scoped.t6ToTable6
import sbt.Setting
import sbt.richInitializeTask

object AndroidLaunch {

  val startTask = (
    (adbTarget, dbPath, streams, manifestSchema, manifestPackage, manifestPath) map {
      (adbTarget, dbPath, streams, manifestSchema, manifestPackage, manifestPath) =>
        adbTarget.startApp(dbPath, streams, manifestSchema, manifestPackage, manifestPath)
        ()
    })

  lazy val settings: Seq[Setting[_]] =
    AndroidInstall.settings ++
      (Seq(
        start <<= startTask,
        start <<= start dependsOn install))
}
