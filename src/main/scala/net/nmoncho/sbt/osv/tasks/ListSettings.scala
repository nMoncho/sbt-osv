/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.tasks

import net.nmoncho.sbt.osv.tasks.Scan.CheckSettings
import sbt.Logger

object ListSettings {

  def apply(settings: CheckSettings)(implicit log: Logger): Unit = {
    log.info(settings.scopes.toPrettyString().split('\n').mkString("\t", "\n\t", ""))
    log.info(settings.engineSettings.toPrettyString().split('\n').mkString("\t", "\n\t", ""))
    log.info(s"\tOutput directory: ${settings.outputDirectory.getAbsolutePath}")
  }

}
