/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.settings

import scala.util.matching.Regex

import net.nmoncho.sbt.osv.SuppressionRule
import sbt.File
import sbt.internal.util.Attributed

/** Suppression Settings
  *
  * @param files           suppression file
  * @param packagedFilter  which dependencies should be considered when importing packaged suppression rules
  */
case class SuppressionSettings(
    file: File,
    suppressions: Set[SuppressionRule],
    packagedEnabled: Boolean,
    packagedFilter: SuppressionSettings.PackagedFilter
)

object SuppressionSettings {

  final val DefaultPackagedFilter: PackagedFilter = PackagedFilter.BlacklistAll

  final val DefaultSuppressionsFilename: String = ".osvignore"

  final val Default: SuppressionSettings = new SuppressionSettings(
    file            = new File(DefaultSuppressionsFilename),
    suppressions    = Set.empty,
    packagedEnabled = false,
    packagedFilter  = DefaultPackagedFilter
  )

  def apply(
      file: File                         = new File(DefaultSuppressionsFilename),
      suppressions: Set[SuppressionRule] = Set.empty,
      packagedEnabled: Boolean           = Default.packagedEnabled,
      packagedFilter: PackagedFilter     = Default.packagedFilter
  ): SuppressionSettings =
    new SuppressionSettings(
      file,
      suppressions,
      packagedEnabled,
      packagedFilter
    )

  type PackagedFilter = Attributed[File] => Boolean

  object PackagedFilter {

    final val BlacklistAll: PackagedFilter = _ => false

    final val WhitelistAll: PackagedFilter = _ => true

    /** Filter dependencies based on their GAV identifiers
      *
      * @param pred a function that takes a GAV (GroudId, ArtifactId, Version) return true if it should consider that artifact.
      */
    def ofGav(pred: (String, String, String) => Boolean): PackagedFilter = {
      (dependency: Attributed[File]) =>
        import sbtcompat.PluginCompat.*

        dependency
          .get(moduleIDStr)
          .map(parseModuleIDStrAttribute)
          .exists(m => pred(m.organization, m.name, m.revision))
    }

    /** Filter dependencies based on a file check
      */
    def ofFile(pred: File => Boolean): PackagedFilter =
      (dependency: Attributed[File]) => pred(dependency.data)

    /** Filter dependencies based on a filename check
      */
    def ofFilename(pred: String => Boolean): PackagedFilter =
      (dependency: Attributed[File]) => pred(dependency.data.getName)

    /** Filter dependencies based on a filename check against a Regex
      */
    def ofFilenameRegex(regex: Regex): PackagedFilter =
      (dependency: Attributed[File]) => regex.findFirstMatchIn(dependency.data.getName).nonEmpty
  }
}
