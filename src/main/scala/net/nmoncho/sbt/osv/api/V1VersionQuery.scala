package net.nmoncho.sbt.osv.api

case class V1VersionQuery(
    name: Option[String]                = None,
    fileHashes: Option[Seq[V1FileHash]] = None
)
