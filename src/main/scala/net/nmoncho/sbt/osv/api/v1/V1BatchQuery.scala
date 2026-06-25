/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1

import net.nmoncho.sbt.osv.Dependency
import net.nmoncho.sbt.osv.api.SnakeCaseConfig.macroRW
import net.nmoncho.sbt.osv.api.SnakeCaseConfig.{ ReadWriter => RW }

/** Batch query format.
  *
  * @param queries The queries that form this batch query.
  */
case class V1BatchQuery(queries: Seq[V1Query])

object V1BatchQuery {

  implicit val rw: RW[V1BatchQuery] = macroRW

  def of(dependencies: Dependency*): V1BatchQuery = V1BatchQuery(dependencies.map(V1Query.of))
}
