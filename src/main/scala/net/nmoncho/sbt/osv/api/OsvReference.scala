package net.nmoncho.sbt.osv.api

import upickle.default.{macroRW, ReadWriter as RW}

case class OsvReference(
    `type`: Option[OsvReferenceType] = None,
    url: Option[String]              = None
)

object OsvReference {
  implicit val rw: RW[OsvReference] = macroRW
}
