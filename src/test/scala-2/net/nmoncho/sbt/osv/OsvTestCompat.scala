/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import sbt.ModuleID
import sbt.internal.util.AttributeEntry
import sbt.internal.util.AttributeMap
import sbt.internal.util.Attributed

object OsvTestCompat {
  def attributeMap(module: ModuleID): AttributeMap =
    AttributeMap(AttributeEntry(sbt.Keys.moduleID.key, module))

  def makeAttributed(module: ModuleID, file: java.io.File): Attributed[java.io.File] =
    Attributed.blank(file).put(sbt.Keys.moduleID.key, module)
}
