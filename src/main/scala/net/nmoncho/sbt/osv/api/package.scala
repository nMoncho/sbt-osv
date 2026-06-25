/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.time.Instant

import net.nmoncho.sbt.osv.api.SnakeCaseConfig.{ ReadWriter => RW }

package object api {

  implicit val instantReadWriter: RW[Instant] =
    SnakeCaseConfig.readwriter[String].bimap(_.toString, Instant.parse)
}
