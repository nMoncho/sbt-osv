/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1

/** The version query.
  *
  * @param name The name of the dependency. Can be empty.
  * @param fileHashes file hashes
  */
case class V1VersionQuery(
    name: Option[String]                = None,
    fileHashes: Option[Seq[V1FileHash]] = None
)
