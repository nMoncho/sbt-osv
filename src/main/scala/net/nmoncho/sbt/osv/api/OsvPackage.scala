package net.nmoncho.sbt.osv.api

import net.nmoncho.sbt.osv.Dependency
import upickle.default.{ macroRW, ReadWriter as RW }

case class OsvPackage(
    name: Option[String]      = None,
    ecosystem: Option[String] = None,
    purl: Option[String]      = None
)

object OsvPackage {
  implicit val rw: RW[OsvPackage] = macroRW

  def forDependency(dependency: Dependency): OsvPackage = OsvPackage(
    name      = Some(s"${dependency.groupId}:${dependency.groupId}"),
    ecosystem = Some("Maven"),
    purl      = None // TODO
  )

}
