/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.io.File
import java.sql.Connection
import java.time.Instant

import net.nmoncho.sbt.osv.api.OsvVulnerability
import net.nmoncho.sbt.osv.api.RpcStatus
import net.nmoncho.sbt.osv.api.v1.Client
import net.nmoncho.sbt.osv.api.v1.V1BatchQuery
import net.nmoncho.sbt.osv.api.v1.V1BatchVulnerabilityList
import net.nmoncho.sbt.osv.api.v1.V1Query
import net.nmoncho.sbt.osv.api.v1.V1VulnerabilityList
import net.nmoncho.sbt.osv.settings.EngineSettings
import net.nmoncho.sbt.osv.storage.ConnectionProvider
import net.nmoncho.sbt.osv.storage.VulnerabilityRepository
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import sbt.Logger

class EngineSpec extends munit.FunSuite {

  implicit val log: Logger = Logger.Null

  test("queries not found in the cache should be queried against the client") {
    val client       = mock(classOf[Client])
    val dbProvider   = ConnectionProvider.h2InMemory()
    val repo         = mock(classOf[VulnerabilityRepository])
    val repoProvider = (_: Connection) => repo

    when(repo.findCached(any(), any())).thenReturn(None)
    when(client.queryBatch(any())(any())).thenReturn(Right(V1BatchVulnerabilityList(None)))

    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      dbProvider,
      repoProvider
    )

    val dep = Dependency("org.foo", "bar", "1.0.0", new File("foo.jar"))

    engine.analyzeDependencies(0.0, Set(dep), Set.empty)

    verify(repo, times(1)).findCached(any(), any())
    verify(client, times(1)).queryBatch(any())(any())
  }

  test("queries found in the cache shouldn't be queried against the client") {
    val client       = mock(classOf[Client])
    val dbProvider   = ConnectionProvider.h2InMemory()
    val repo         = mock(classOf[VulnerabilityRepository])
    val repoProvider = (_: Connection) => repo

    // Returning empty seq, which means no vulnerabilities for this dependency
    when(repo.findCached(any(), any())).thenReturn(Some(Seq.empty))

    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      dbProvider,
      repoProvider
    )

    val dep = Dependency("org.foo", "bar", "1.0.0", new File("foo.jar"))

    engine.analyzeDependencies(0.0, Set(dep), Set.empty)

    verifyNoInteractions(client)
  }

  test("dependencies without vulnerabilities should be cached as empty") {
    val client       = mock(classOf[Client])
    val dbProvider   = ConnectionProvider.h2InMemory()
    val repo         = mock(classOf[VulnerabilityRepository])
    val repoProvider = (_: Connection) => repo

    // Cache miss, then OSV reports no vulnerabilities for the dependency
    when(repo.findCached(any(), any())).thenReturn(None)
    when(client.queryBatch(any())(any())).thenReturn(
      Right(V1BatchVulnerabilityList(Some(Vector(V1BatchVulnerabilityList.Value(None)))))
    )

    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      dbProvider,
      repoProvider
    )

    val dep = Dependency("org.foo", "bar", "1.0.0", new File("foo.jar"))

    engine.analyzeDependencies(0.0, Set(dep), Set.empty)

    // Next run hits the cache instead of querying OSV again
    verify(repo, times(1)).cache(V1Query.of(dep), Seq.empty)
  }

  // -------------------------------------------------------------------------
  // Suppression matching: match by primary id AND aliases
  // -------------------------------------------------------------------------

  private val suppressionDep = Dependency("org.foo", "bar", "1.0.0", new File("foo.jar"))

  private def osvVuln(id: String, aliases: String*): OsvVulnerability =
    OsvVulnerability(
      id            = id,
      schemaVersion = "1.0.0",
      summary       = "",
      details       = "",
      affected      = Seq.empty,
      aliases       = if (aliases.isEmpty) None else Some(aliases.toSeq)
    )

  /** Builds an engine whose cache returns the given advisories for any query, so
    * the client is never contacted and only the suppression logic is exercised.
    */
  private def engineReturning(vulns: OsvVulnerability*): Engine.Default = {
    val client = mock(classOf[Client])
    val repo   = mock(classOf[VulnerabilityRepository])
    when(repo.findCached(any(), any())).thenReturn(Some(vulns.toSeq))
    new Engine.Default(
      EngineSettings.Default,
      client,
      ConnectionProvider.h2InMemory(),
      (_: Connection) => repo
    )
  }

  private def rule(name: String): SuppressionRule = SuppressionRule(name, "", "")

  test("a CVE-named suppression suppresses a GHSA-primary advisory via its alias") {
    val engine = engineReturning(osvVuln("GHSA-aaaa-bbbb-cccc", "CVE-2022-1234"))

    val result = engine.analyzeDependencies(
      0.0,
      Set(suppressionDep),
      Set(rule("CVE-2022-1234"))
    )

    assert(
      result.vulnerabilities.getOrElse(suppressionDep, Set.empty).isEmpty,
      "the advisory should have been suppressed"
    )
    assertEquals(result.suppressed.map(_.id), Set("GHSA-aaaa-bbbb-cccc"))
    assertEquals(result.unusedSuppressions, Set.empty[SuppressionRule])
  }

  test("a GHSA-named suppression suppresses a CVE-primary advisory via its alias") {
    val engine = engineReturning(osvVuln("CVE-2022-1234", "GHSA-aaaa-bbbb-cccc"))

    val result = engine.analyzeDependencies(
      0.0,
      Set(suppressionDep),
      Set(rule("GHSA-aaaa-bbbb-cccc"))
    )

    assert(result.vulnerabilities.getOrElse(suppressionDep, Set.empty).isEmpty)
    assertEquals(result.suppressed.map(_.id), Set("CVE-2022-1234"))
    assertEquals(result.unusedSuppressions, Set.empty[SuppressionRule])
  }

  test("an exact primary-id suppression still works") {
    val engine = engineReturning(osvVuln("CVE-2022-1234"))

    val result = engine.analyzeDependencies(
      0.0,
      Set(suppressionDep),
      Set(rule("CVE-2022-1234"))
    )

    assert(result.vulnerabilities.getOrElse(suppressionDep, Set.empty).isEmpty)
    assertEquals(result.suppressed.map(_.id), Set("CVE-2022-1234"))
  }

  test("a non-matching suppression leaves the advisory and is reported as unused") {
    val ruleThatMatchesNothing = rule("CVE-9999-0000")
    val engine                 = engineReturning(osvVuln("GHSA-aaaa-bbbb-cccc", "CVE-2022-1234"))

    val result = engine.analyzeDependencies(
      0.0,
      Set(suppressionDep),
      Set(ruleThatMatchesNothing)
    )

    assertEquals(
      result.vulnerabilities.getOrElse(suppressionDep, Set.empty).map(_.id),
      Set("GHSA-aaaa-bbbb-cccc")
    )
    assert(result.suppressed.isEmpty)
    assertEquals(result.unusedSuppressions, Set(ruleThatMatchesNothing))
  }

  // -------------------------------------------------------------------------
  // Database is best-effort: a failing cache never fails the scan
  // -------------------------------------------------------------------------

  private val dbDep = Dependency("org.foo", "bar", "1.0.0", new File("foo.jar"))

  test("a database read failure is treated as a cache miss and falls back to the OSV API") {
    val client = mock(classOf[Client])
    val repo   = mock(classOf[VulnerabilityRepository])
    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      ConnectionProvider.h2InMemory(),
      (_: Connection) => repo
    )

    when(repo.findCached(any(), any())).thenThrow(new RuntimeException("database is locked"))
    when(client.queryBatch(any())(any())).thenReturn(Right(V1BatchVulnerabilityList(None)))

    // Must not throw: the read error degrades to a cache miss.
    val result = engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)

    verify(repo, times(1)).findCached(any(), any())
    verify(client, times(1)).queryBatch(any())(any())
    assert(result.vulnerabilities.getOrElse(dbDep, Set.empty).isEmpty)
  }

  test("a database write failure is ignored and the scan still returns results") {
    val client = mock(classOf[Client])
    val repo   = mock(classOf[VulnerabilityRepository])
    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      ConnectionProvider.h2InMemory(),
      (_: Connection) => repo
    )

    // Cache miss forces the API path, whose result the engine then tries to cache.
    when(repo.findCached(any(), any())).thenReturn(None)
    val batch = V1BatchVulnerabilityList(
      Some(
        Vector(
          V1BatchVulnerabilityList.Value(
            Some(Vector(V1BatchVulnerabilityList.OsvBatchVulnerability("GHSA-xxxx", Instant.EPOCH)))
          )
        )
      )
    )
    when(client.queryBatch(any())(any())).thenReturn(Right(batch))
    when(client.query(any())(any()))
      .thenReturn(Right(V1VulnerabilityList(Some(Vector(osvVuln("GHSA-xxxx"))))))
    doThrow(new RuntimeException("disk full")).when(repo).cache(any(), any())

    // Must not throw even though caching fails.
    val result = engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)

    verify(repo, times(1)).cache(any(), any())
    assertEquals(
      result.vulnerabilities.getOrElse(dbDep, Set.empty).map(_.id),
      Set("GHSA-xxxx")
    )
  }

  test("a database that cannot be opened disables the cache and falls back to the OSV API") {
    val client = mock(classOf[Client])
    // Simulates CI contention on the shared H2 file: opening a connection fails.
    val failingDb = new ConnectionProvider {
      override def connection(): Connection =
        throw new RuntimeException("cannot open shared H2 file (locked)")
      override def close(): Unit = ()
    }
    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      failingDb,
      (_: Connection) => mock(classOf[VulnerabilityRepository])
    )

    when(client.queryBatch(any())(any())).thenReturn(Right(V1BatchVulnerabilityList(None)))

    // Must not throw: the whole cache is disabled for this run.
    val result = engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)
    engine.close() // closing a never-opened cache must also be safe

    verify(client, times(1)).queryBatch(any())(any())
    assert(result.vulnerabilities.getOrElse(dbDep, Set.empty).isEmpty)
  }

  // -------------------------------------------------------------------------
  // Withdrawn advisories are excluded from the findings
  // -------------------------------------------------------------------------

  private def osvVulnWithdrawn(id: String): OsvVulnerability =
    osvVuln(id).copy(withdrawn = Some(Instant.EPOCH))

  test("withdrawn advisories are excluded from the results") {
    val engine = engineReturning(osvVuln("GHSA-active"), osvVulnWithdrawn("GHSA-withdrawn"))

    val result = engine.analyzeDependencies(0.0, Set(suppressionDep), Set.empty)

    val ids = result.vulnerabilities.getOrElse(suppressionDep, Set.empty).map(_.id)
    assert(ids.contains("GHSA-active"), s"active advisory should be reported: $ids")
    assert(!ids.contains("GHSA-withdrawn"), s"withdrawn advisory must be excluded: $ids")
  }

  // -------------------------------------------------------------------------
  // Analysis timeout bounds the whole analysis
  // -------------------------------------------------------------------------

  private def engineWith(settings: EngineSettings, client: Client): Engine.Default = {
    val repo = mock(classOf[VulnerabilityRepository])
    when(repo.findCached(any(), any())).thenReturn(None)
    new Engine.Default(settings, client, ConnectionProvider.h2InMemory(), (_: Connection) => repo)
  }

  test("analysis exceeding the configured timeout fails with a clear error") {
    val client                                             = mock(classOf[Client])
    val batch: Either[RpcStatus, V1BatchVulnerabilityList] = Right(V1BatchVulnerabilityList(None))
    when(client.queryBatch(any())(any())).thenAnswer { (_: InvocationOnMock) =>
      Thread.sleep(3000) // longer than the timeout below
      batch
    }
    val settings =
      EngineSettings.Default.copy(analysisTimeout = Some(java.time.Duration.ofMillis(100)))

    val ex = intercept[IllegalStateException] {
      engineWith(settings, client).analyzeDependencies(0.0, Set(dbDep), Set.empty)
    }
    assert(ex.getMessage.toLowerCase.contains("timeout"), ex.getMessage)
  }

  test("analysis within the configured timeout completes normally") {
    val client = mock(classOf[Client])
    when(client.queryBatch(any())(any())).thenReturn(Right(V1BatchVulnerabilityList(None)))
    val settings =
      EngineSettings.Default.copy(analysisTimeout = Some(java.time.Duration.ofSeconds(10)))

    val result = engineWith(settings, client).analyzeDependencies(0.0, Set(dbDep), Set.empty)

    assert(result.vulnerabilities.getOrElse(dbDep, Set.empty).isEmpty)
  }

  // -------------------------------------------------------------------------
  // An unavailable OSV API fails closed with a clear message
  // -------------------------------------------------------------------------

  test("an unavailable OSV API fails the scan with a clear, actionable message") {
    val client = mock(classOf[Client])
    val repo   = mock(classOf[VulnerabilityRepository])
    when(repo.findCached(any(), any())).thenReturn(None)
    when(client.queryBatch(any())(any()))
      .thenReturn(
        Left(RpcStatus(None, Some("Could not reach the OSV API. Cause: connect timed out")))
      )

    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      ConnectionProvider.h2InMemory(),
      (_: Connection) => repo
    )

    val ex = intercept[IllegalStateException] {
      engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)
    }
    assert(ex.getMessage.contains("OSV API was unavailable"), ex.getMessage)
    assert(ex.getMessage.contains("connect timed out"), ex.getMessage)
  }

  // -------------------------------------------------------------------------
  // querybatch is chunked to OSV's 1000-query-per-request cap
  // -------------------------------------------------------------------------

  test("querybatch is split into chunks no larger than the OSV per-request cap") {
    val client = mock(classOf[Client])
    val repo   = mock(classOf[VulnerabilityRepository])
    when(repo.findCached(any(), any())).thenReturn(None) // all uncached -> all go to the API

    val batchSizes = scala.collection.mutable.ListBuffer.empty[Int]
    when(client.queryBatch(any())(any())).thenAnswer { (inv: InvocationOnMock) =>
      val batch = inv.getArgument[V1BatchQuery](0)
      batchSizes += batch.queries.size
      // Return a result aligned with the request: one empty entry per query.
      val results = batch.queries.map(_ => V1BatchVulnerabilityList.Value(None)).toVector
      Right(V1BatchVulnerabilityList(Some(results))): Either[RpcStatus, V1BatchVulnerabilityList]
    }

    val engine = new Engine.Default(
      EngineSettings.Default,
      client,
      ConnectionProvider.h2InMemory(),
      (_: Connection) => repo
    )

    val deps =
      (1 to 1500).map(i => Dependency("org.foo", s"lib-$i", "1.0.0", new File(s"lib-$i.jar"))).toSet

    engine.analyzeDependencies(0.0, deps, Set.empty)

    assertEquals(batchSizes.size, 2, s"expected 2 chunks for 1500 deps, got ${batchSizes.toList}")
    assert(
      batchSizes.forall(_ <= Engine.MaxBatchQueries),
      s"a chunk exceeded ${Engine.MaxBatchQueries}: ${batchSizes.toList}"
    )
    assertEquals(batchSizes.sum, 1500, "every dependency should be queried exactly once")
  }

  // -------------------------------------------------------------------------
  // Purging the cache evicts every entry
  // -------------------------------------------------------------------------

  test("purging the cache forces the next scan to query the OSV API") {
    val client = mock(classOf[Client])
    when(client.queryBatch(any())(any())).thenReturn(
      Right(V1BatchVulnerabilityList(Some(Vector(V1BatchVulnerabilityList.Value(None)))))
    )

    // Private, unnamed in-memory H2: never touches the user's osv.db nor other tests' DBs
    val db           = ConnectionProvider.h2InMemory()
    val repoProvider = (conn: Connection) => {
      ConnectionProvider.createSchema(conn)
      VulnerabilityRepository.jdbc(conn)
    }
    val engine = new Engine.Default(EngineSettings.Default, client, db, repoProvider)

    try {
      // Scan, purge, scan: both scans must reach the API
      engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)
      engine.purgeCache()
      engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)

      verify(client, times(2)).queryBatch(any())(any())
    } finally {
      engine.close()
    }
  }

  test("a purge after a scan is persisted to the database file") {
    val client = mock(classOf[Client])
    when(client.queryBatch(any())(any())).thenReturn(
      Right(V1BatchVulnerabilityList(Some(Vector(V1BatchVulnerabilityList.Value(None)))))
    )

    // Throwaway file DB: never touches the user's osv.db nor other tests' DBs
    val dir          = java.nio.file.Files.createTempDirectory("osv-purge-spec").toFile
    val dbFile       = new File(dir, Engine.DefaultOsvDB)
    val repoProvider = (conn: Connection) => {
      ConnectionProvider.createSchema(conn)
      VulnerabilityRepository.jdbc(conn)
    }
    def engine() =
      new Engine.Default(
        EngineSettings.Default,
        client,
        ConnectionProvider.h2InFile(dbFile),
        repoProvider
      )

    try {
      // Scan then purge on the same connection, then close
      val first = engine()
      first.analyzeDependencies(0.0, Set(dbDep), Set.empty)
      first.purgeCache()
      first.close()

      // A fresh engine must not find the purged entry
      val second = engine()
      second.analyzeDependencies(0.0, Set(dbDep), Set.empty)
      second.close()

      verify(client, times(2)).queryBatch(any())(any())
    } finally {
      sbt.io.IO.delete(dir)
    }
  }

  test("without purging, the next scan is served from the cache") {
    val client = mock(classOf[Client])
    when(client.queryBatch(any())(any())).thenReturn(
      Right(V1BatchVulnerabilityList(Some(Vector(V1BatchVulnerabilityList.Value(None)))))
    )

    val db           = ConnectionProvider.h2InMemory()
    val repoProvider = (conn: Connection) => {
      ConnectionProvider.createSchema(conn)
      VulnerabilityRepository.jdbc(conn)
    }
    val engine = new Engine.Default(EngineSettings.Default, client, db, repoProvider)

    try {
      // Control for the purge test: proves the cache is actually populated
      engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)
      engine.analyzeDependencies(0.0, Set(dbDep), Set.empty)

      verify(client, times(1)).queryBatch(any())(any())
    } finally {
      engine.close()
    }
  }

}
