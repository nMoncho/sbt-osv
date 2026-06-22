package net.nmoncho.sbt.osv

import net.nmoncho.sbt.osv.settings.EngineSettings

trait Engine {

  def analyzeDependencies(dependencies: Set[Dependency], suppressions: Set[String]): Map[Dependency, Set[Vulnerability]] = ???

  def close(): Unit

  def writeReports(projectName: String, outputDir: sbt.File, str: String ): Unit = ???

}

object Engine {
  def create(settings: EngineSettings): Engine = ???
}
