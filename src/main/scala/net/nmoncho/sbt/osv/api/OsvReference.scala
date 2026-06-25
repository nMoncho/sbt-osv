/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

/** Reference URL.
  *
  * @param `type` The type of the reference.
  * @param url The URL.
  */
case class OsvReference(
    `type`: OsvReferenceType,
    url: String
)

object OsvReference {
  implicit val rw: RW[OsvReference] = macroRW
}
