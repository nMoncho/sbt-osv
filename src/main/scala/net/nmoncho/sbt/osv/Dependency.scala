/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import scala.util.Try

import com.github.packageurl.PackageURL
import sbt.File
import sbt.librarymanagement.ModuleID

/** Dependency to scan
  *
  * @param groupId group or organization id
  * @param artifactId artifact id
  * @param revision revision or version
  * @param file file pointing to the dependency (ie. JAR)
  */
case class Dependency(groupId: String, artifactId: String, revision: String, file: File) {

  /** <a href="https://github.com/package-url/purl-spec">Package URL</a> for this dependency */
  def purl: String =
    new PackageURL("maven", groupId, artifactId, revision, null, null).canonicalize()

  /** Maven coordinates in Gradle format */
  def coordinates: String = s"${groupId}:${artifactId}:${revision}"

}

object Dependency {

  /** Constructs a Dependency from its <a href="https://github.com/package-url/purl-spec">Package URL</a>
    *
    * @param purl package url as a string
    * @return if successful parsing, a valid Dependency
    */
  def of(purl: String): Try[Dependency] = Try {
    val parsed = new PackageURL(purl)

    Dependency(
      parsed.getNamespace,
      parsed.getName,
      parsed.getVersion,
      new File(s"${parsed.getName}-${parsed.getVersion}.jar")
    )
  }

  /** Constructs a Dependency from its ModuleID */
  def of(moduleID: ModuleID): Dependency =
    Dependency(
      moduleID.organization,
      moduleID.name,
      moduleID.revision,
      new File(s"${moduleID.name}-${moduleID.revision}.jar")
    )

}
