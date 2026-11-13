/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import net.nmoncho.sbt.osv.Dependency
import net.nmoncho.sbt.osv.MavenVersion
import net.nmoncho.sbt.osv.api.SnakeCaseConfig.macroRW
import net.nmoncho.sbt.osv.api.SnakeCaseConfig.{ ReadWriter => RW }

/** Affected versions and commits.
  *
  * @param `package` Package information.
  * @param ranges Range information.
  * @param versions List of affected versions.
  * @param severity Severity of the vulnerability for this package.
  */
case class OsvAffected(
    `package`: OsvPackage,
    ranges: Option[Seq[OsvRange]]      = None,
    versions: Option[Seq[String]]      = None,
    severity: Option[Seq[OsvSeverity]] = None
) {

  /** Whether this entry describes `dependency`, e.g. "org.example:lib" */
  def covers(dependency: Dependency): Boolean =
    `package`.name == OsvPackage.of(dependency).name

  /** Whether any range declares events, i.e. affected versions are known */
  def hasEvents: Boolean =
    ranges.exists(_.exists(_.events.nonEmpty))

  /** Lowest fix above `revision`, so it closes the range holding it.
    * e.g. events [1.0, fixed 1.5.1, 2.0, fixed 2.10.0] at 2.1.0 -> 2.10.0
    * GIT ranges hold commit hashes, not versions, so they're skipped.
    */
  def fixedAfter(revision: String): Option[String] = {
    val fixes = for {
      range <- ranges.getOrElse(Seq.empty) if range.`type` != OsvRangeType.GIT
      event <- range.events
      fixed <- event.fixed if MavenVersion.ordering.gt(fixed, revision)
    } yield fixed

    if (fixes.isEmpty) None else Some(fixes.min(MavenVersion.ordering))
  }
}

object OsvAffected {
  implicit val rw: RW[OsvAffected] = macroRW
}
