/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1

/** Result of DetmineVersion.
  */
case class V1VersionMatchList(
    matches: Option[Seq[V1VersionMatch]] = None
)
