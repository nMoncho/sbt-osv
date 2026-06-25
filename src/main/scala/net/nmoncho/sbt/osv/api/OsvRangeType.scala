/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.{ ReadWriter => RW }

/** Type of the version information.
  */
sealed trait OsvRangeType
object OsvRangeType {
  case object UNSPECIFIED extends OsvRangeType
  case object GIT extends OsvRangeType
  case object SEMVER extends OsvRangeType
  case object ECOSYSTEM extends OsvRangeType

  def fromString(s: String): OsvRangeType = s match {
    case "UNSPECIFIED" => UNSPECIFIED
    case "GIT" => GIT
    case "SEMVER" => SEMVER
    case "ECOSYSTEM" => ECOSYSTEM
    case other => throw new IllegalArgumentException(s"Unknown OsvRangeType: $other")
  }

  implicit val rw: RW[OsvRangeType] = SnakeCaseConfig
    .readwriter[String]
    .bimap(_.toString, fromString)
}
