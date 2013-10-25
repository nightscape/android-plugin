package sbtandroid

import AndroidHelpers.isWindows
import AndroidPlugin.dbPath
import AndroidPlugin.emulatorStart
import AndroidPlugin.emulatorStop
import AndroidPlugin.killAdb
import AndroidPlugin.listDevices
import AndroidPlugin.sdkPath
import AndroidPlugin.toolsPath
import sbt.File
import sbt.InputTask
import sbt.Keys.aggregate
import sbt.Keys.streams
import sbt.Path
import sbt.PathFinder
import sbt.Project
import sbt.Scoped.t2ToTable2
import sbt.Setting
import sbt.State
import sbt.Task
import sbt.TaskKey
import sbt.complete.DefaultParsers.Space
import sbt.complete.DefaultParsers.literal
import sbt.complete.DefaultParsers.richParser
import sbt.complete.DefaultParsers.token
import sbt.globFilter
import sbt.richFile
import sbt.richInitialize
import sbt.singleFileFinder
import sbt.stringToProcess
import sbt.Def

object AndroidEmulator {
  private def emulatorStartTask = (parsedTask: TaskKey[String]) =>
    (parsedTask, toolsPath) map { (avd, toolsPath) =>
      "%s/emulator -avd %s".format(toolsPath, avd).run
      ()
    }

  private def listDevicesTask: Def.Initialize[Task[Unit]] = (dbPath) map {
    _ + " devices" !
  }

  private def killAdbTask: Def.Initialize[Task[Unit]] = (dbPath) map {
    _ + " kill-server" !
  }

  private def emulatorStopTask = (dbPath, streams) map { (dbPath, s) =>
    s.log.info("Stopping emulators")
    val serial = "%s -e get-serialno".format(dbPath).!!
    "%s -s %s emu kill".format(dbPath, serial).!
    ()
  }

  def installedAvds(sdkHome: File) = (s: State) => {
    val avds = ((Path.userHome / ".android" / "avd" * "*.ini") +++
      (if (isWindows) (sdkHome / ".android" / "avd" * "*.ini")
      else PathFinder.empty)).get
    Space ~> avds.map(f => token(f.base))
      .reduceLeftOption(_ | _).getOrElse(token("none"))
  }

  private def emulatorStartTask2 = (s: State) => (sdkPath) map {
    (p) => (installedAvds(p))
  }
  
  lazy val baseSettings: Seq[Setting[_]] = (Seq(
    listDevices <<= listDevicesTask,
    killAdb <<= killAdbTask,
    emulatorStart <<= Def.inputTask(emulatorStartTask2),
    
    //emulatorStart <<= InputTask((sdkPath)(installedAvds(_)))(emulatorStartTask),
    emulatorStop <<= emulatorStopTask))

  lazy val aggregateSettings: Seq[Setting[_]] = Seq(
    listDevices,
    emulatorStart,
    emulatorStop) map { aggregate in _ := false }

  lazy val settings: Seq[Setting[_]] = baseSettings ++ aggregateSettings
}
