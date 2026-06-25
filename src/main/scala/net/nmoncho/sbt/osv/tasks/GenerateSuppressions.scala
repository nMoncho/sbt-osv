/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package tasks

import net.nmoncho.sbt.osv.Keys.osvSkip
import net.nmoncho.sbt.osv.Keys.osvSuppressions
import net.nmoncho.sbt.osv.settings.SuppressionSettings
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

/** Generates the XML Suppression File containing suppressions specified
  * using [[SuppressionRule]]s
  */
object GenerateSuppressions {

  lazy val forProject: Def.Initialize[Task[Set[SuppressionRule]]] =
    Def.taskDyn {
      if (!thisProject.value.autoPlugins.contains(JvmPlugin) || (osvSkip ?? false).value)
        Def.task(Set.empty)
      else
        Def.task {
          implicit val log: Logger = streams.value.log
          val settings             = osvSuppressions.value
          val dependencies         = AllProjectsScan.dependencies().value

          val buildSuppressions            = settings.suppressions
          val importedPackagedSuppressions = collectImportedPackagedSuppressions(
            settings,
            dependencies
          )

          (buildSuppressions ++ importedPackagedSuppressions).toSet
        }
    }

  /** Collects all [[SuppressionRule]]s packaged on the libraries included in this project.
    *
    * @return list of all packaged [[SuppressionRule]]s
    */
  def collectImportedPackagedSuppressions(
      settings: SuppressionSettings,
      dependencies: Set[Attributed[File]]
  )(implicit log: Logger): Seq[SuppressionRule] =
    if (settings.packagedEnabled) {
      val allowedDependencies = dependencies.filter(settings.packagedFilter).toSeq

      IO.withTemporaryDirectory { tempDir =>
        allowedDependencies.flatMap { dependency =>
          IO.unzip(
            dependency.data,
            tempDir,
            (filename: String) => filename == SuppressionSettings.DefaultSuppressionsFilename
          ).flatMap { file =>
            log.debug(s"Extracting packaged suppressions file from JAR [${file.name}]")

            parseSuppressionFile(file)
          }
        }
      }
    } else {
      log.debug("Packaged suppressions rules disabled, skipping...")
      Seq.empty[SuppressionRule]
    }

  /** Creates a file containing [[SuppressionRule]]s defined in the [[net.nmoncho.sbt.dependencycheck.settings.SuppressionSettings.files]] field
    * and the [[net.nmoncho.sbt.dependencycheck.settings.SuppressionSettings.suppressions]] field.
    *
    * @return a sequence of files if packaged suppressions are enabled, empty otherwise.
    */
  def exportPackagedSuppressions(): Def.Initialize[Task[Seq[File]]] = Def.taskDyn {
    implicit val log: Logger     = streams.value.log
    val settings                 = osvSuppressions.value
    val packagedSuppressionsFile =
      (Compile / resourceManaged).value / SuppressionSettings.DefaultSuppressionsFilename

    if (settings.packagedEnabled) {
      Def.task {
        val generated = writeExportSuppressions(packagedSuppressionsFile, settings)

        if (generated) {
          Seq(packagedSuppressionsFile)
        } else {
          Seq.empty
        }
      }
    } else {
      log.info(
        "Packaged suppressions is disabled, skipping packaged suppression file generation..."
      )
      Def.task(Seq.empty)
    }
  }

  /** Writes the exported packaged suppression rules
    *
    * Packaged suppressions rules will only consider the rules defined in the
    * [[net.nmoncho.sbt.dependencycheck.settings.SuppressionSettings.files]] field
    * and the [[net.nmoncho.sbt.dependencycheck.settings.SuppressionSettings.suppressions]] field.
    *
    * @param file file to write to
    * @param settings rules settings, including files and
    * @return true if a file was generated, false if there was not suppression rule to write to the file
    */
  private[tasks] def writeExportSuppressions(file: File, settings: SuppressionSettings)(
      implicit log: Logger
  ): Boolean = {
    val buildSuppressions = settings.suppressions
    val suppressionsFiles = if (settings.file.exists()) {
      log.debug(s"Including suppressions rules from [${file.name}]")
      parseSuppressionFile(settings.file)
    } else {
      log.debug(s"Ignoring [${settings.file}] on packaged suppressions rules export process")
      Seq.empty
    }

    val rules = buildSuppressions ++ suppressionsFiles

    if (rules.nonEmpty) {
      log.info(
        s"Generating packaged suppression file to [${file.getAbsolutePath}]"
      )

      SuppressionParser.write(file, rules)
      true
    } else {
      log.info("No suppressions defined, skipping packaged suppression file generation...")
      false
    }
  }

  private[tasks] def parseSuppressionFile(file: File)(
      implicit log: Logger
  ): Set[SuppressionRule] =
    try {
      SuppressionParser.parse(file)
    } catch {
      case t: Throwable =>
        log.warn(
          s"Failed parsing suppression rules from file [${file.name}], skipping file..."
        )
        logThrowable(t)
        Set.empty[SuppressionRule]
    }
}
