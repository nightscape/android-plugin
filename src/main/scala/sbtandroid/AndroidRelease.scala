package sbtandroid

import AndroidPlugin.apk
import AndroidPlugin.cachePasswords
import AndroidPlugin.cleanAligned
import AndroidPlugin.keyalias
import AndroidPlugin.keystorePath
import AndroidPlugin.packageAlignedName
import AndroidPlugin.packageAlignedPath
import AndroidPlugin.packageApkPath
import AndroidPlugin.release
import AndroidPlugin.signRelease
import AndroidPlugin.toolsPath
import AndroidPlugin.versionName
import AndroidPlugin.zipAlign
import AndroidPlugin.zipAlignPath
import sbt.File
import sbt.Path
import sbt.IO
import sbt.Keys.artifact
import sbt.Keys.streams
import sbt.Keys.target
import sbt.ProcessIO
import sbt.Project
import sbt.Scoped.t2ToTable2
import sbt.Scoped.t4ToTable4
import sbt.Scoped.t5ToTable5
import sbt.Setting
import sbt.Task
import sbt.richFile
import sbt.richInitializeTask
import sbt.stringSeqToProcess
import sbt.Def

object AndroidRelease {

  def zipAlignTask: Def.Initialize[Task[File]] =
    (zipAlignPath, packageApkPath, packageAlignedPath, streams) map { (zip, apkPath, pPath, s) =>
      val zipAlign = Seq(
        zip.absolutePath,
        "-v", "4",
        apkPath.absolutePath,
        pPath.absolutePath)
      s.log.debug("Aligning " + zipAlign.mkString(" "))
      s.log.debug(zipAlign !!)
      s.log.info("Aligned " + pPath)
      pPath
    }

  def signReleaseTask: Def.Initialize[Task[File]] =
    (keystorePath, keyalias, packageApkPath, streams, cachePasswords) map { (ksPath, ka, pPath, s, cache) =>
      val jarsigner = Seq(
        "jarsigner",
        "-verbose",
        "-sigalg", "SHA1withRSA",
        "-digestalg", "SHA1",
        "-keystore", ksPath.absolutePath,
        "-storepass", PasswordManager.get(
          "keystore", ka, cache).getOrElse(sys.error("could not get password")),
        pPath.absolutePath,
        ka)
      s.log.debug("Signing " + jarsigner.mkString(" "))
      val out = new StringBuffer
      val exit = jarsigner.run(new ProcessIO(input => (),
        output => out.append(IO.readStream(output)),
        error => out.append(IO.readStream(error)),
        inheritedInput => false)).exitValue()
      if (exit != 0) sys.error("Error signing: " + out)
      s.log.debug(out.toString)
      s.log.info("Signed " + pPath)
      pPath
    }

  private def releaseTask = (packageAlignedPath, streams) map { (path, s) =>
    s.log.success("Ready for publication: \n" + path)
    path
  }

  lazy val settings: Seq[Setting[_]] = (Seq(
    // Configuring Settings
    keystorePath := Path.userHome / ".keystore",
    zipAlignPath <<= (toolsPath) { _ / "zipalign" },
    packageAlignedName <<= (artifact, versionName) map ((a, v) =>
      String.format("%s-signed-%s.apk", a.name, v)),
    packageAlignedPath <<= (target, packageAlignedName) map (_ / _),

    // Configuring Tasks
    cleanAligned <<= (packageAlignedPath) map (IO.delete(_)),

    release <<= releaseTask,
    release <<= release dependsOn zipAlign,

    zipAlign <<= zipAlignTask,
    zipAlign <<= zipAlign dependsOn (signRelease, cleanAligned),

    signRelease <<= signReleaseTask,
    signRelease <<= signRelease dependsOn apk))
}
