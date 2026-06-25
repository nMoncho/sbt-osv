/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import sbt.ModuleID
import sbt.internal.util.AttributeEntry
import sbt.internal.util.AttributeMap

object OsvTestCompat {
  def attributeMap(module: ModuleID): AttributeMap =
    AttributeMap(AttributeEntry(sbt.Keys.moduleID.key, module))
}
