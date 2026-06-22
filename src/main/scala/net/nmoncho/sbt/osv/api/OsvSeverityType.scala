package net.nmoncho.sbt.osv.api

import upickle.default.ReadWriter as RW

sealed trait OsvSeverityType
object OsvSeverityType {
  case object UNSPECIFIED extends OsvSeverityType
  case object CVSS_V4 extends OsvSeverityType
  case object CVSS_V3 extends OsvSeverityType
  case object CVSS_V2 extends OsvSeverityType

  def fromString(s: String): OsvSeverityType = s match {
    case "UNSPECIFIED" => UNSPECIFIED
    case "CVSS_V4"     => CVSS_V4
    case "CVSS_V3"     => CVSS_V3
    case "CVSS_V2"     => CVSS_V2
    case other         => throw new IllegalArgumentException(s"Unknown OsvSeverityType: $other")
  }

  implicit val rw: RW[OsvSeverityType] = upickle.default
    .readwriter[String]
    .bimap(_.toString, fromString)
}
