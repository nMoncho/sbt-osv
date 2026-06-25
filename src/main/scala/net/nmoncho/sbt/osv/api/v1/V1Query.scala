/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1

import net.nmoncho.sbt.osv.Dependency
import net.nmoncho.sbt.osv.api.OsvPackage
import net.nmoncho.sbt.osv.api.SnakeCaseConfig.macroRW
import net.nmoncho.sbt.osv.api.SnakeCaseConfig.{ ReadWriter => RW }

/** Query format.
  *
  * @param commit The commit hash to query for. If specified, `version` should not be set.
  * @param version The version string to query for. A fuzzy match is done against upstream
  *                versions. If specified, `commit` should not be set.
  * @param `package` The package to query against. When a `commit` hash is given, this is
  *                  optional.
  * @param pageToken next page token, if fetching new pages
  */
case class V1Query(
    commit: Option[String]        = None,
    version: Option[String]       = None,
    `package`: Option[OsvPackage] = None,
    pageToken: Option[String]     = None
)

object V1Query {
  implicit val rw: RW[V1Query] = macroRW

  def of(dependency: Dependency): V1Query =
    V1Query(
      version   = Some(dependency.revision),
      `package` = Some(OsvPackage.of(dependency))
    )
}
