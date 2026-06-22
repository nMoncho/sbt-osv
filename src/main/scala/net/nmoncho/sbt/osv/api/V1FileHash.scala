package net.nmoncho.sbt.osv.api

import sbt.internal.shaded.com.google.protobuf.ByteString

case class V1FileHash(
    filePath: Option[String] = None,
    hashType: Option[String] = None,
    hash: Option[ByteString] = None
)
