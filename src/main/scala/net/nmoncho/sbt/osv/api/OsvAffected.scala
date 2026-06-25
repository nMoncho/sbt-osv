/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

/** Affected versions and commits.
  *
  * @param `package` Package information.
  * @param ranges Range information.
  * @param versions List of affected versions.
  * @param severity Severity of the vulnerability for this package.
  */
case class OsvAffected(
    `package`: OsvPackage,
    ranges: Option[Seq[OsvRange]]      = None,
    versions: Option[Seq[String]]      = None,
    severity: Option[Seq[OsvSeverity]] = None
)

object OsvAffected {
  implicit val rw: RW[OsvAffected] = macroRW
}
