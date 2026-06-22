package net.nmoncho.sbt.osv.api

sealed trait ProtobufNullValue
object ProtobufNullValue {
  case object NULL_VALUE extends ProtobufNullValue
}
