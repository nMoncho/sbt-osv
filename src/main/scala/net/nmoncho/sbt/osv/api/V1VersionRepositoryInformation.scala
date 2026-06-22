package net.nmoncho.sbt.osv.api

import sbt.internal.shaded.com.google.protobuf.ByteString

case class V1VersionRepositoryInformation(
    `type`: Option[V1VersionRepositoryInformationRepoType] = None,
    address: Option[String]                                = None,
    commit: Option[ByteString]                             = None,
    tag: Option[String]                                    = None,
    version: Option[String]                                = None
)
