/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1

/** Version Repository Information
  *
  * @param `type` information type
  * @param address Source address of the repository.
  * @param commit Commit hash.
  * @param tag Commit tag
  * @param version Parsed version from commit tag
  */
case class V1VersionRepositoryInformation(
    `type`: Option[V1VersionRepositoryInformationRepoType] = None,
    address: Option[String]                                = None,
    commit: Option[String]                                 = None,
    tag: Option[String]                                    = None,
    version: Option[String]                                = None
)
