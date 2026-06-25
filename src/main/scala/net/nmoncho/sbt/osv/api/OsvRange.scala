/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

/** Affected ranges.
  *
  * @param `type` The type of version information.
  * @param repo Required if type is GIT. The publicly accessible URL of the repo that can
  *             be directly passed to clone commands.
  * @param events Version event information.
  */
case class OsvRange(
    `type`: OsvRangeType,
    repo: Option[String]  = None,
    events: Seq[OsvEvent] = Seq.empty
)

object OsvRange {
  implicit val rw: RW[OsvRange] = macroRW
}
