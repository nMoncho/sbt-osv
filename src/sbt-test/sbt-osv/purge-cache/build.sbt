import net.nmoncho.sbt.osv.settings.EngineSettings
import sbtcompat.PluginCompat.*

version := "0.1"
lazy val root = project.in(file("."))
scalaVersion := "2.13.16"

libraryDependencies += "commons-beanutils" % "commons-beanutils" % "1.9.1"

// Isolated cache inside this scripted test's sandbox: never the shared/local osv.db
lazy val cacheDb = settingKey[File]("Isolated OSV cache database")
cacheDb := baseDirectory.value / "osv-cache" / "osv.db"

osvEngineSettings := EngineSettings.Default.copy(dataDirectory = Some(cacheDb.value))

lazy val assertCached = taskKey[Unit]("Fails when the OSV cache is empty")
// Uncached: sbt 2 would otherwise replay a previous result instead of reading the DB
assertCached := Def.uncached {
  val count = CacheProbe.cachedQueries(cacheDb.value)
  if (count == 0) {
    sys.error("Expected cached OSV queries, found none")
  }
}

lazy val assertPurged = taskKey[Unit]("Fails when the OSV cache has entries")
assertPurged := Def.uncached {
  val count = CacheProbe.cachedQueries(cacheDb.value)
  if (count != 0) {
    sys.error(s"Expected an empty OSV cache, found [$count] cached queries")
  }
}
