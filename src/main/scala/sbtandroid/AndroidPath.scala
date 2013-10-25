package sbtandroid

import AndroidHelpers.compareVersions
import AndroidHelpers.determineAndroidSdkPath
import AndroidHelpers.osBatchSuffix
import AndroidPlugin.aaptName
import AndroidPlugin.aaptPath
import AndroidPlugin.adbName
import AndroidPlugin.aidlName
import AndroidPlugin.buildToolsPath
import AndroidPlugin.buildToolsVersion
import AndroidPlugin.dbPath
import AndroidPlugin.dxName
import AndroidPlugin.dxPath
import AndroidPlugin.envs
import AndroidPlugin.idlPath
import AndroidPlugin.osDxName
import AndroidPlugin.platformToolsPath
import AndroidPlugin.sdkPath
import AndroidPlugin.toolsPath
import sbt.File
import sbt.Keys.baseDirectory
import sbt.Keys.resolvers
import sbt.Scoped.t2ToApp2
import sbt.Scoped.t3ToApp3
import sbt.Setting
import sbt.richFile
import sbt.toRepositoryName

object AndroidPath {
  private def determineBuildToolsVersion(sdkPath: File): Option[String] = {
    // Find out which versions of the build tools are installed
    val buildToolsPath = (sdkPath / "build-tools")

    // If this path doesn't exist, just set the version to ""
    if (!buildToolsPath.exists) None

    // Else, sort the installed versions and take the most recent
    else {
      // List the files in the build-tools directory
      val files = buildToolsPath.listFiles.toList

      // List the different versions
      val versions = files map (_.name) filterNot (_.startsWith("."))

      // Sort the versions by the most recent
      val sorted = versions.sortWith(compareVersions(_, _))

      // Return the most recent
      sorted.headOption
    }
  }

  lazy val settings: Seq[Setting[_]] = {
    AndroidDefaults.settings ++ Seq(
      osDxName <<= (dxName)(_ + osBatchSuffix),

      toolsPath <<= (sdkPath)(_ / "tools"),
      dbPath <<= (platformToolsPath, adbName)(_ / _),
      platformToolsPath <<= (sdkPath)(_ / "platform-tools"),
      buildToolsVersion <<= (sdkPath)(determineBuildToolsVersion _),
      buildToolsPath <<= (sdkPath, platformToolsPath, buildToolsVersion) { (sdkPath, platformToolsPath, buildToolsVersion) =>
        buildToolsVersion match {
          case Some(v) => sdkPath / "build-tools" / v
          case None => platformToolsPath
        }
      },
      aaptPath <<= (buildToolsPath, aaptName)(_ / _),
      idlPath <<= (buildToolsPath, aidlName)(_ / _),
      dxPath <<= (buildToolsPath, osDxName)(_ / _),

      sdkPath <<= (envs, baseDirectory) { determineAndroidSdkPath(_, _) },

      // Add the Google repository
      resolvers <+= (sdkPath) { p =>
        "Google Repository" at (
          (p / "extras" / "google" / "m2repository").toURI.toString)
      },
      resolvers <+= (sdkPath) { p =>
        "Android Support Repository" at (
          (p / "extras" / "android" / "m2repository").toURI.toString)
      })
  }
}
