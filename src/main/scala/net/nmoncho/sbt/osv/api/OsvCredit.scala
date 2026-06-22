package net.nmoncho.sbt.osv.api

import upickle.default.{macroRW, ReadWriter as RW}

case class OsvCredit(
    name: Option[String]          = None,
    contact: Option[Seq[String]]  = None,
    `type`: Option[OsvCreditType] = None
)

object OsvCredit {
  implicit val rw: RW[OsvCredit] = macroRW
}
