package net.nmoncho.sbt.osv.api

sealed trait V1VersionRepositoryInformationRepoType
object V1VersionRepositoryInformationRepoType {
  case object UNSPECIFIED extends V1VersionRepositoryInformationRepoType
  case object GIT extends V1VersionRepositoryInformationRepoType
}
