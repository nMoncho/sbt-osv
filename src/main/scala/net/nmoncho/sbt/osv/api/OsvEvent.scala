/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

/** Version events.
  *
  * @param introduced The earliest version/commit where this vulnerability
  *                   was introduced in.
  * @param fixed The version/commit that this vulnerability was fixed in.
  * @param limit The limit to apply to the range.
  * @param lastAffected The last affected version.
  */
case class OsvEvent(
    introduced: Option[String]   = None,
    fixed: Option[String]        = None,
    limit: Option[String]        = None,
    lastAffected: Option[String] = None
)

object OsvEvent {
  implicit val rw: RW[OsvEvent] = macroRW
}
