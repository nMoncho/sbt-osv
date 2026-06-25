/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1
import net.nmoncho.sbt.osv.api.OsvPackage

/** Match information for the provided VersionQuery.
  *
  * @param score Score in the interval (0.0, 1.0] with 1.0 being a perfect match.
  * @param repoInfo Information about the upstream repository.
  * @param osvIdentifier The OSV identifier.
  * @param cpe23 CPE 2.3.
  */
case class V1VersionMatch(
    score: Option[Double]                            = None,
    repoInfo: Option[V1VersionRepositoryInformation] = None,
    osvIdentifier: Option[OsvPackage]                = None,
    cpe23: Option[String]                            = None
)
