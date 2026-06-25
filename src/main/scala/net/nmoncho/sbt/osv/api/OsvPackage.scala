/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import net.nmoncho.sbt.osv.Dependency

import SnakeCaseConfig.macroRW
import SnakeCaseConfig.{ ReadWriter => RW }

/** Package information and version.
  *
  * @param name Name of the package. Should match the name used in the package
  *             ecosystem (e.g. the npm package name). For C/C++ projects integrated in
  *             OSS-Fuzz, this is the name used for the integration.
  * @param ecosystem The ecosystem for this package.
  *                  For the complete list of valid ecosystem names, see
  *                  <https://ossf.github.io/osv-schema/#affectedpackage-field>.
  * @param purl The package URL for this package.
  */
case class OsvPackage(
    name: String,
    ecosystem: String,
    purl: Option[String] = None
)

object OsvPackage {
  implicit val rw: RW[OsvPackage] = macroRW

  def of(dependency: Dependency): OsvPackage = OsvPackage(
    name      = s"${dependency.groupId}:${dependency.artifactId}",
    ecosystem = "Maven",
    purl      = None // either `name` or `purl`, not both
  )

}
