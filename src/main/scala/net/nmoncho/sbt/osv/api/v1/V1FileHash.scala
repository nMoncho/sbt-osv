/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api.v1

/** Information about the files in the repository
  * to identify the version.
  *
  * @param filePath The file path inside the repository, relative to the repository root.
  * @param hashType
  * @param hash
  */
case class V1FileHash(
    filePath: Option[String] = None,
    hashType: Option[String] = None,
    hash: Option[String]     = None
)
