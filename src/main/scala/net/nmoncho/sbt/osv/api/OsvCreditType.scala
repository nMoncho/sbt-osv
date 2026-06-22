package net.nmoncho.sbt.osv.api

import upickle.default.ReadWriter as RW

sealed trait OsvCreditType
object OsvCreditType {
  case object UNSPECIFIED extends OsvCreditType
  case object OTHER extends OsvCreditType
  case object FINDER extends OsvCreditType
  case object REPORTER extends OsvCreditType
  case object ANALYST extends OsvCreditType
  case object COORDINATOR extends OsvCreditType
  case object REMEDIATION_DEVELOPER extends OsvCreditType
  case object REMEDIATION_REVIEWER extends OsvCreditType
  case object REMEDIATION_VERIFIER extends OsvCreditType
  case object TOOL extends OsvCreditType
  case object SPONSOR extends OsvCreditType

  def fromString(s: String): OsvCreditType = s match {
    case "UNSPECIFIED"           => UNSPECIFIED
    case "OTHER"                 => OTHER
    case "FINDER"                => FINDER
    case "REPORTER"              => REPORTER
    case "ANALYST"               => ANALYST
    case "COORDINATOR"           => COORDINATOR
    case "REMEDIATION_DEVELOPER" => REMEDIATION_DEVELOPER
    case "REMEDIATION_REVIEWER"  => REMEDIATION_REVIEWER
    case "REMEDIATION_VERIFIER"  => REMEDIATION_VERIFIER
    case "TOOL"                  => TOOL
    case "SPONSOR"               => SPONSOR
    case other                   => throw new IllegalArgumentException(s"Unknown OsvCreditType: $other")
  }

  implicit val rw: RW[OsvCreditType] = upickle.default
    .readwriter[String]
    .bimap(_.toString, fromString)
}
