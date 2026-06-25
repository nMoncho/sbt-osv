/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import com.github.packageurl.PackageURL
import sbt.File
import sbt.librarymanagement.ModuleID

case class Dependency(groupId: String, artifactId: String, revision: String, file: File) {

  def purl: String =
    new PackageURL("maven", groupId, artifactId, revision, null, null).canonicalize()

  def coordinates: String = s"${groupId}:${artifactId}:${revision}"

}

object Dependency {

  def of(purl: String): Dependency = {
    val parsed = new PackageURL(purl)

    Dependency(parsed.getNamespace, parsed.getName, parsed.getVersion, new File("tmp.jar"))
  }

  def of(moduleID: ModuleID): Dependency =
    Dependency(moduleID.organization, moduleID.name, moduleID.revision, new File("tmp.jar"))

}
