/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

/** Who has the credit for a vulnerability
  *
  * @param name The name to give credit to.
  * @param contact Contact methods (URLs).
  * @param `type` The type or role of the individual or entity being credited.
  */
case class OsvCredit(
    name: Option[String]          = None,
    contact: Option[Seq[String]]  = None,
    `type`: Option[OsvCreditType] = None
)

object OsvCredit {
  implicit val rw: RW[OsvCredit] = macroRW
}
