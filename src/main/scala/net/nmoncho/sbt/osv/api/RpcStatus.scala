/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

case class RpcStatus(
    code: Option[Int]       = None,
    message: Option[String] = None
)

object RpcStatus {
  implicit val rw: RW[RpcStatus] = macroRW
}
