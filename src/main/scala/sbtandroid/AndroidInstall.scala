package sbtandroid

import proguard.{ Configuration => ProGuardConfiguration, ProGuard, ConfigurationParser, ConfigurationWriter }
import java.io.{File => JFile}
import java.util.Properties
import AndroidHelpers.dxMemoryParameter
import AndroidPlugin.aaptPackage
import AndroidPlugin.aaptPath
import AndroidPlugin.adbTarget
import AndroidPlugin.apk
import AndroidPlugin.cleanApk
import AndroidPlugin.copyNativeLibraries
import AndroidPlugin.dbPath
import AndroidPlugin.dx
import AndroidPlugin.dxInputs
import AndroidPlugin.dxMemory
import AndroidPlugin.dxOutputPath
import AndroidPlugin.dxPath
import AndroidPlugin.dxPredex
import AndroidPlugin.generatedProguardConfigPath
import AndroidPlugin.includedClasspath
import AndroidPlugin.install
import AndroidPlugin.libraryJarPath
import AndroidPlugin.mainAssetsPath
import AndroidPlugin.manifestPackage
import AndroidPlugin.manifestPath
import AndroidPlugin.nativeDirectories
import AndroidPlugin.packageApkPath
import AndroidPlugin.packageConfig
import AndroidPlugin.proguard
import AndroidPlugin.proguardConfiguration
import AndroidPlugin.proguardInJarsFilter
import AndroidPlugin.proguardOptimizations
import AndroidPlugin.proguardOptions
import AndroidPlugin.proguardOutputPath
import AndroidPlugin.providedClasspath
import AndroidPlugin.resPath
import AndroidPlugin.resourcesApkPath
import AndroidPlugin.toolsPath
import AndroidPlugin.uninstall
import AndroidPlugin.useDebug
import AndroidPlugin.useProguard
import sbt.File
import sbt.IO
import sbt.Keys.classDirectory
import sbt.Keys.compile
import sbt.Keys.resourceDirectory
import sbt.Keys.scalaInstance
import sbt.Keys.sourceManaged
import sbt.Keys.streams
import sbt.Keys.target
import sbt.Project
import sbt.Scoped.t11ToTable11
import sbt.Scoped.t3ToTable3
import sbt.Scoped.t4ToTable4
import sbt.Scoped.t7ToTable7
import sbt.Setting
import sbt.Task
import sbt.filesToFinder
import sbt.globFilter
import sbt.richFile
import sbt.richInitializeTask
import sbt.singleFileFinder
import sbt.stringSeqToProcess
import sbt.Def

object AndroidInstall {

  /**
   * Task that installs a package on the target
   */
  private val installTask =
    (adbTarget, dbPath, packageApkPath, streams) map { (t, dp, p, s) =>
      s.log.info("Installing %s".format(p.name))
      t.installPackage(dp, s, p)
      ()
    }

  /**
   * Task that uninstalls a package from the target
   */
  private val uninstallTask =
    (adbTarget, dbPath, manifestPackage, streams) map { (t, dp, p, s) =>
      s.log.info("Uninstalling %s".format(p))
      t.uninstallPackage(dp, s, p)
      ()
    }

  private def aaptPackageTask: Def.Initialize[Task[File]] =
    (aaptPath, manifestPath, resPath, mainAssetsPath, libraryJarPath, resourcesApkPath, streams) map {
      (aaptPath, manifestPath, resPath, mainAssetsPath, libraryJarPath, resourcesApkPath, streams) =>

        // Make assets directory
        mainAssetsPath.mkdirs

        // Resource arguments
        val libraryResPathArgs = resPath.flatMap(p => Seq("-S", p.absolutePath))

        // AAPT command line
        val aapt = Seq(aaptPath.absolutePath, "package",
          "--auto-add-overlay", "-f",
          "-M", manifestPath.head.absolutePath,
          "-A", mainAssetsPath.absolutePath,
          "-I", libraryJarPath.absolutePath,
          "-F", resourcesApkPath.absolutePath) ++
          libraryResPathArgs

        // Package resources
        streams.log.info("Packaging resources in " + resourcesApkPath.absolutePath)
        streams.log.debug("Running: " + aapt.mkString(" "))
        if (aapt.run(false).exitValue != 0) sys.error("Error packaging resources")

        // Return the path to the resources APK
        resourcesApkPath
    }

  private def dxTask: Def.Initialize[Task[File]] =
    (dxPath, dxMemory, target, proguard, dxInputs, dxPredex,
      proguardOptimizations, classDirectory, dxOutputPath, scalaInstance, streams) map {
        (dxPath, dxMemory, target, proguard, dxInputs, dxPredex,
        proguardOptimizations, classDirectory, dxOutputPath, scalaInstance, streams) =>

          // Main dex command
          def dexing(inputs: Seq[JFile], output: JFile) {
            val uptodate = output.exists && inputs.forall(input =>
              input.isDirectory match {
                case true =>
                  (input ** "*").get.forall(_.lastModified <= output.lastModified)
                case false =>
                  input.lastModified <= output.lastModified
              })

            if (!uptodate) {
              val noLocals = if (proguardOptimizations.isEmpty) "" else "--no-locals"
              val dxCmd = (Seq(dxPath.absolutePath,
                dxMemoryParameter(dxMemory),
                "--dex", noLocals,
                "--num-threads=" + java.lang.Runtime.getRuntime.availableProcessors,
                "--output=" + output.getAbsolutePath) ++
                inputs.map(_.absolutePath)).filter(_.length > 0)
              streams.log.debug(dxCmd.mkString(" "))
              streams.log.info("Dexing " + output.getAbsolutePath)
              streams.log.debug(dxCmd !!)
            } else streams.log.debug("DEX file " + output.getAbsolutePath + "is up to date, skipping")
          }

          // First, predex the inputs in dxPredex
          val dxPredexInputs = dxInputs filter (dxPredex contains _) map { jarPath =>

            // Generate the output path
            val outputPath = target / (jarPath.getName + ".apk")

            // Predex the library
            dexing(Seq(jarPath), outputPath)

            // Return the output path
            outputPath
          }

          // Non-predexed inputs
          val dxClassInputs = dxInputs filterNot (dxPredex contains _)

          // Generate the final DEX
          dexing(dxClassInputs +++ dxPredexInputs get, dxOutputPath)

          // Return the path to the generated final DEX file
          dxOutputPath
      }

  private def proguardTask: Def.Initialize[Task[Option[File]]] =
    (proguardConfiguration, proguardOutputPath, streams) map {
      (proguardConfiguration, proguardOutputPath, streams) =>

        proguardConfiguration map { configFile =>
          // Execute Proguard
          streams.log.info("Executing Proguard with configuration file " + configFile.getAbsolutePath)

          // Parse the configuration
          val config = new ProGuardConfiguration
          val parser = new ConfigurationParser(configFile, new Properties)
          parser.parse(config)

          // Execute ProGuard
          val proguard = new ProGuard(config)
          proguard.execute

          // Return the proguard-ed output JAR
          proguardOutputPath
        }
    }

  private def proguardConfigurationTask: Def.Initialize[Task[Option[File]]] =
    (useProguard, proguardOptimizations, classDirectory,
      generatedProguardConfigPath, includedClasspath, providedClasspath,
      proguardOutputPath, manifestPackage, proguardOptions, sourceManaged,
      proguardInJarsFilter) map {

        (useProguard, proguardOptimizations, classDirectory,
        genConfig, includedClasspath, providedClasspath,
        proguardOutputPath, manifestPackage, proguardOptions, sourceManaged,
        proguardInJarsFilter) =>

          if (useProguard) {

            val generatedOptions =
              if (genConfig.exists())
                scala.io.Source.fromFile(genConfig).getLines.filterNot(x => x.isEmpty || x.head == '#').toSeq
              else Seq()

            val optimizationOptions = if (proguardOptimizations.isEmpty) Seq("-dontoptimize") else proguardOptimizations
            val sep = JFile.pathSeparator

            // Input class files
            val inClass = "\"" + classDirectory.absolutePath + "\""

            // Input library JARs to be included in the APK
            val inJars = includedClasspath
              .map { jar => "\"%s\"(%s)".format(jar, proguardInJarsFilter(jar).mkString(",")) }
              .mkString(sep)

            // Input library JARs to be provided at runtime
            val inLibrary = providedClasspath
              .map("\"" + _.absolutePath + "\"")
              .mkString(sep)

            // Output JAR
            val outJar = "\"" + proguardOutputPath.absolutePath + "\""

            // Proguard arguments
            val args = (
              "-injars" :: inClass ::
              "-injars" :: inJars ::
              "-outjars" :: outJar ::
              "-libraryjars" :: inLibrary ::
              Nil) ++
              generatedOptions ++
              optimizationOptions ++ (
                "-dontwarn" :: "-dontobfuscate" ::
                "-dontnote scala.Enumeration" ::
                "-dontnote org.xml.sax.EntityResolver" ::
                "-keep public class * extends android.app.backup.BackupAgent" ::
                "-keep public class * extends android.appwidget.AppWidgetProvider" ::
                "-keep class scala.collection.SeqLike { public java.lang.String toString(); }" ::
                "-keep class scala.reflect.ScalaSignature" ::
                "-keep public class * implements junit.framework.Test { public void test*(); }" ::
                """
                  -keepclassmembers class * implements java.io.Serializable {
                    private static final java.io.ObjectStreamField[] serialPersistentFields;
                    private void writeObject(java.io.ObjectOutputStream);
                    private void readObject(java.io.ObjectInputStream);
                    java.lang.Object writeReplace();
                    java.lang.Object readResolve();
                   }
                   """ :: Nil) ++ proguardOptions

            // Instantiate the Proguard configuration
            val config = new ProGuardConfiguration
            new ConfigurationParser(args.toArray[String], new Properties).parse(config)

            // Write that to a file
            val configFile = sourceManaged / "proguard.txt"
            val writer = new ConfigurationWriter(configFile)
            writer.write(config)
            writer.close

            // Return the configuration file
            Some(configFile)

          } else None
      }

  private val apkTask =
    (useDebug, packageConfig, streams) map { (debug, c, s) =>
      val builder = new ApkBuilder(c, debug)
      builder.build.fold(sys.error(_), s.log.info(_))
      s.log.debug(builder.outputStream.toString)
      c.packageApkPath
    }

  lazy val settings: Seq[Setting[_]] = Seq(

    // Resource package generation
    aaptPackage <<= aaptPackageTask,

    // Dexing (DX)
    dx <<= dxTask,

    // Clean generated APK
    cleanApk <<= (packageApkPath) map (IO.delete(_)),

    // Proguard
    proguard <<= proguardTask,
    proguard <<= proguard dependsOn (compile),

    // Proguard configuration
    proguardConfiguration <<= proguardConfigurationTask,

    // Final APK generation
    packageConfig <<=
      (toolsPath, packageApkPath, resourcesApkPath, dxOutputPath,
        nativeDirectories, dxInputs, resourceDirectory) map
        (ApkConfig(_, _, _, _, _, _, _)),

    apk <<= apkTask dependsOn (cleanApk, aaptPackage, dx, copyNativeLibraries),

    // Package installation
    install <<= installTask dependsOn apk,

    // Package uninstallation
    uninstall <<= uninstallTask)
}
