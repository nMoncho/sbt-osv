/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.io.File
import java.sql.Connection

import net.nmoncho.sbt.osv.api.v1.Client
import net.nmoncho.sbt.osv.api.v1.V1BatchVulnerabilityList
import net.nmoncho.sbt.osv.settings.EngineSettings
import net.nmoncho.sbt.osv.storage.ConnectionProvider
import net.nmoncho.sbt.osv.storage.VulnerabilityRepository
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
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

}
