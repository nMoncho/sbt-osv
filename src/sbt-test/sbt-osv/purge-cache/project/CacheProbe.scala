import java.io.File

import net.nmoncho.sbt.osv.storage.ConnectionProvider

// Inspects the OSV cache database, referenced from `build.sbt` assertions
object CacheProbe {

  /** Number of cached OSV queries in the database at `dbFile` */
  def cachedQueries(dbFile: File): Int = {
    val db = ConnectionProvider.h2InFile(dbFile)

    try {
      val rs = db.connection().createStatement().executeQuery("SELECT COUNT(*) FROM osv_queries")
      rs.next()
      rs.getInt(1)
    } finally {
      db.close()
    }
  }
}
