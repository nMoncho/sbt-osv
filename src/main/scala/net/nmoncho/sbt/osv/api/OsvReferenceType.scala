/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import SnakeCaseConfig.{ ReadWriter => RW }

sealed trait OsvReferenceType
object OsvReferenceType {
  case object NONE extends OsvReferenceType
  case object WEB extends OsvReferenceType
  case object ADVISORY extends OsvReferenceType
  case object REPORT extends OsvReferenceType
  case object FIX extends OsvReferenceType
  case object PACKAGE extends OsvReferenceType
  case object ARTICLE extends OsvReferenceType
  case object EVIDENCE extends OsvReferenceType

  def fromString(s: String): OsvReferenceType = s match {
    case "NONE" => NONE
    case "WEB" => WEB
    case "ADVISORY" => ADVISORY
    case "REPORT" => REPORT
    case "FIX" => FIX
    case "PACKAGE" => PACKAGE
    case "ARTICLE" => ARTICLE
    case "EVIDENCE" => EVIDENCE
    case other => throw new IllegalArgumentException(s"Unknown OsvReferenceType: $other")
  }

  implicit val rw: RW[OsvReferenceType] = SnakeCaseConfig
    .readwriter[String]
    .bimap(_.toString, fromString)
}
