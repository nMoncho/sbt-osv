package net.nmoncho.sbt.osv.api

case class V1BatchQuery(
    queries: Option[Seq[V1Query]] = None
)
