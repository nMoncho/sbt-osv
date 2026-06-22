package net.nmoncho.sbt.osv.api

import upickle.default.{macroRW, ReadWriter as RW}

case class OsvRange(
    `type`: Option[OsvRangeType]  = None,
    repo: Option[String]          = None,
    events: Option[Seq[OsvEvent]] = None
)

object OsvRange {
  implicit val rw: RW[OsvRange] = macroRW
}