package net.nmoncho.sbt.osv.api

import upickle.default.{macroRW, ReadWriter as RW}

case class OsvSeverity(
    `type`: Option[OsvSeverityType] = None,
    score: Option[String]           = None
)

object OsvSeverity {
  implicit val rw: RW[OsvSeverity] = macroRW
}
