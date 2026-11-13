/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.time.Duration

import net.nmoncho.sbt.osv.settings._
import sbt._

object Keys {

  // Settings
  lazy val osvFailBuildOnCVSS: SettingKey[Double] = settingKey(
    "Specifies if the build should be failed if a CVSS score above a specified level is identified. The default is 11 which means since the CVSS scores are 0-10, by default the build will never fail. More information on CVSS scores can be found at https://nvd.nist.gov/vuln-metrics/cvss"
  )

  lazy val osvSkip: SettingKey[Boolean] = settingKey(
    "Skips this project during the OSV vulnerability scan."
  )

  lazy val osvScopes: SettingKey[ScopesSettings] = settingKey(
    "What library dependency scopes are considered during the analysis."
  )

  lazy val osvAnalysisTimeout: SettingKey[Option[Duration]] =
    settingKey("Maximum wall-clock time allowed for the whole OSV analysis. Unbounded when unset.")

  lazy val osvOutputDirectory: SettingKey[File] =
    settingKey("The location to write the report(s).")

  lazy val osvEngineSettings: SettingKey[EngineSettings] =
    settingKey("Scan engine settings.")

  lazy val osvSuppressions: SettingKey[SuppressionSettings] = settingKey(
    "Suppression settings used to ignore known false positives: inline rules, an `.osvignore` file, and optionally suppressions packaged inside dependency JARs."
  )

  lazy val osvReportFormats: SettingKey[Seq[ReportGenerator]] = settingKey(
    "The report formats to be generated."
  )

  lazy val osvConnectionTimeout: SettingKey[Option[Duration]] = settingKey(
    "HTTP connection timeout for OSV API requests. Falls back to the client default (10 seconds) when unset."
  )

  lazy val osvConnectionReadTimeout: SettingKey[Option[Duration]] = settingKey(
    "HTTP read timeout for OSV API requests. Falls back to the client default (10 seconds) when unset."
  )

  // Tasks
  lazy val osvScan: InputKey[Unit] = inputKey(
    "Runs osv scan against the project and generates a report per sub project."
  )
  lazy val osvListSettings: InputKey[Unit] = inputKey(
    "Prints the effective osvScan settings for each sub-project, without running a scan."
  )
  lazy val osvListSuppressions: InputKey[Unit] = inputKey(
    "Lists the active suppression rules for each project, both those defined in the project (build.sbt or .osvignore) and those imported from packaged suppressions."
  )
  lazy val osvPurgeCache: TaskKey[Unit] = taskKey(
    "Purges the OSV vulnerability database (ie. evicts the cache)"
  )
}
