package net.nmoncho.sbt.osv.api

import upickle.default.{ macroRW, ReadWriter as RW }

sealed trait OsvRangeType
object OsvRangeType {
  case object UNSPECIFIED extends OsvRangeType
  case object GIT extends OsvRangeType
  case object SEMVER extends OsvRangeType
  case object ECOSYSTEM extends OsvRangeType

  def fromString(s: String): OsvRangeType = s match {
    case "UNSPECIFIED" => UNSPECIFIED
    case "GIT"         => GIT
    case "SEMVER"      => SEMVER
    case "ECOSYSTEM"   => ECOSYSTEM
    case other         => throw new IllegalArgumentException(s"Unknown OsvRangeType: $other")
  }

  implicit val rw: RW[OsvRangeType] = upickle.default
    .readwriter[String]
    .bimap(_.toString, fromString)
}
