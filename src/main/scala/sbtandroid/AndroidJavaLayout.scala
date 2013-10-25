package sbtandroid

import AndroidPlugin.assetsDirectoryName
import AndroidPlugin.mainAssetsPath
import AndroidPlugin.mainResPath
import AndroidPlugin.manifestName
import AndroidPlugin.manifestPath
import AndroidPlugin.resDirectoryName
import AndroidPlugin.useProguard
import AndroidPlugin.useTypedLayouts
import AndroidPlugin.useTypedResources
import sbt.Compile
import sbt.ConfigKey.configurationToKey
import sbt.GlobalScope
import sbt.Keys.autoScalaLibrary
import sbt.Keys.baseDirectory
import sbt.Keys.javaSource
import sbt.Keys.unmanagedBase
import sbt.Scoped.t2ToApp2
import sbt.Scoped.t2ToTable2
import sbt.Setting
import sbt.richFile
import sbt.richInitialize
import sbt.richInitializeTask

/** Some sensible defaults for building java projects with the plugin */
object AndroidJavaLayout {
  lazy val settings: Seq[Setting[_]] = (Seq(
    autoScalaLibrary in GlobalScope := false,
    useProguard in Compile := false,
    useTypedResources in Compile := false,
    useTypedLayouts in Compile := false,
    manifestPath in Compile <<= (baseDirectory, manifestName) map ((s, m) => Seq(s / m)) map (x => x),
    mainResPath in Compile <<= (baseDirectory, resDirectoryName)(_ / _) map (x => x),
    mainAssetsPath in Compile <<= (baseDirectory, assetsDirectoryName)(_ / _),
    javaSource in Compile <<= (baseDirectory)(_ / "src"),
    unmanagedBase <<= baseDirectory(_ / "libs")))
}
