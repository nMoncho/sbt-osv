package net.nmoncho.sbt.osv.api

import net.nmoncho.sbt.osv.Dependency
import upickle.default.{macroRW, ReadWriter as RW}

case class V1Query(
    commit: Option[String]        = None,
    version: Option[String]       = None,
    `package`: Option[OsvPackage] = None,
    pageToken: Option[String]     = None
)

object V1Query {
  implicit val rw: RW[V1Query] = macroRW

  def forDependency(dependency: Dependency): V1Query =
    V1Query(
      version = Some(dependency.revision),
      `package` = Some(OsvPackage.forDependency(dependency))
    )
}