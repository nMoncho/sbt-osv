/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.{ ReadWriter => RW }

/** Type of the severity. */
sealed trait OsvSeverityType
object OsvSeverityType {
  case object UNSPECIFIED extends OsvSeverityType
  case object CVSS_V4 extends OsvSeverityType
  case object CVSS_V3 extends OsvSeverityType
  case object CVSS_V2 extends OsvSeverityType
  case class OTHER(raw: String) extends OsvSeverityType

  def fromString(s: String): OsvSeverityType = s match {
    case "UNSPECIFIED" => UNSPECIFIED
    case "CVSS_V4" => CVSS_V4
    case "CVSS_V3" => CVSS_V3
    case "CVSS_V2" => CVSS_V2
    case other => OTHER(other)
  }

  implicit val rw: RW[OsvSeverityType] = SnakeCaseConfig
    .readwriter[String]
    .bimap(_.toString, fromString)
}
