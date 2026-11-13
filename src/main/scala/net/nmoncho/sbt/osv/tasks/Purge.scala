/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package tasks

import sbt.Def
import sbt.Keys.streams
import sbt.Logger
import sbt.Task

object Purge {

  def apply: Def.Initialize[Task[Unit]] = Def.task {
    implicit val log: Logger = streams.value.log

    val settings = ScanSettings.allProjectsSettings.value.engineSettings

    withEngine(settings) { engine =>
      try {
        engine.purgeCache()
      } catch {
        case t: Throwable =>
          log.error("Failed to purge OSV cache")
          logThrowable(t)
          throw t
      }
    }

  }
}
