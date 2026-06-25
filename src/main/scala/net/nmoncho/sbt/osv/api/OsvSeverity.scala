/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

/** Vulnerability severity
  *
  * @param `type` The type of this severity entry.
  * @param score The quantitative score.
  */
case class OsvSeverity(
    `type`: Option[OsvSeverityType] = None,
    score: Option[String]           = None
) {

  def typeAsString: String = `type`.map(_.toString).getOrElse("N/A")

}

object OsvSeverity {
  implicit val rw: RW[OsvSeverity] = macroRW
}
