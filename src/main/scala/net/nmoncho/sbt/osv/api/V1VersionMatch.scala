package net.nmoncho.sbt.osv.api

case class V1VersionMatch(
    score: Option[Double]                            = None,
    repoInfo: Option[V1VersionRepositoryInformation] = None,
    osvIdentifier: Option[OsvPackage]                = None,
    cpe23: Option[String]                            = None
)
