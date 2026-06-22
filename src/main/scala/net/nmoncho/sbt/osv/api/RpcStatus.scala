package net.nmoncho.sbt.osv.api

import upickle.default.{ ReadWriter => RW, macroRW }

case class RpcStatus(
    code: Option[Int]       = None,
    message: Option[String] = None
)

object RpcStatus {
  implicit val rw: RW[RpcStatus] = macroRW
}
