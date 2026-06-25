/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1

sealed trait V1VersionRepositoryInformationRepoType
object V1VersionRepositoryInformationRepoType {
  case object UNSPECIFIED extends V1VersionRepositoryInformationRepoType
  case object GIT extends V1VersionRepositoryInformationRepoType
}
