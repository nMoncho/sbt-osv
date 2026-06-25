/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import scala.language.implicitConversions

import sbt.librarymanagement.ModuleID

trait TestUtils {}

object TestUtils {
  implicit def moduleIdToDependency(m: ModuleID): Dependency = Dependency.of(m)
}
