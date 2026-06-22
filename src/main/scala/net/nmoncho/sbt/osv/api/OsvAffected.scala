package net.nmoncho.sbt.osv.api

import upickle.default.{macroRW, ReadWriter as RW}

case class OsvAffected(
    `package`: Option[OsvPackage]      = None,
    ranges: Option[Seq[OsvRange]]      = None,
    versions: Option[Seq[String]]      = None,
    severity: Option[Seq[OsvSeverity]] = None
)

object OsvAffected {
  implicit val rw: RW[OsvAffected] = macroRW
}
