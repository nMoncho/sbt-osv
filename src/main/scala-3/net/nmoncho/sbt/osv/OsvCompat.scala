/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import sbt.Configuration
import sbt.Def.Classpath
import sbt.UpdateReport
import xsbti.FileConverter

private[osv] object OsvCompat {

  def managedJars(
      config: Configuration,
      jarTypes: Set[String],
      udpateReport: UpdateReport,
      converter: FileConverter
  ): Classpath =
    sbt.Classpaths.managedJars(config, jarTypes, udpateReport, converter)

}
