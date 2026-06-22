package net.nmoncho.sbt.osv.api

import upickle.default.{ macroRW, ReadWriter as RW }

case class OsvEvent(
    introduced: Option[String]   = None,
    fixed: Option[String]        = None,
    limit: Option[String]        = None,
    lastAffected: Option[String] = None
)

object OsvEvent {
  implicit val rw: RW[OsvEvent] = macroRW
}
