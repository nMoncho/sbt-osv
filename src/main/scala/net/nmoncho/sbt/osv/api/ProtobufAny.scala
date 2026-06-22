package net.nmoncho.sbt.osv.api

import sbt.internal.shaded.com.google.protobuf.ByteString

case class ProtobufAny(
    typeUrl: Option[String]   = None,
    value: Option[ByteString] = None
)
