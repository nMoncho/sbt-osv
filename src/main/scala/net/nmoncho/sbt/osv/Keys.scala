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
    "Skips this project on the dependency-check analysis."
  )

  lazy val osvScopes: SettingKey[ScopesSettings] = settingKey(
    "What library dependency scopes are considered during the analysis."
  )

  lazy val osvAnalysisTimeout: SettingKey[Option[Duration]] =
    settingKey("Set the analysis timeout.")

  lazy val osvOutputDirectory: SettingKey[File] =
    settingKey("The location to write the report(s).")

  lazy val osvEngineSettings: SettingKey[EngineSettings] =
    settingKey("Scan engine settings.")

  lazy val osvSuppressions: SettingKey[SuppressionSettings] = settingKey(
    "Combines a sequence of file paths, or URLs to the XML suppression files, with any hosted suppressions the analysis should be using. Suppressions are used to ignore false positives."
  )

  lazy val osvReportFormats: SettingKey[Seq[ReportGenerator]] = settingKey(
    "The report formats to be generated."
  )

  lazy val osvConnectionTimeout: SettingKey[Option[Duration]] = settingKey(
    "Sets the URL Connection Timeout (in milliseconds) used when downloading external data."
  )

  lazy val osvConnectionReadTimeout: SettingKey[Option[Duration]] = settingKey(
    "Sets the URL Connection Read Timeout (in milliseconds) used when downloading external data."
  )

  // Tasks
  lazy val osvScan: InputKey[Unit] = inputKey(
    "Runs osv scan against the project and generates a report per sub project."
  )
  lazy val osvListSettings: InputKey[Unit] = inputKey(
    "Runs osv against the project and generates a report per sub project."
  )
  lazy val osvListSuppressions: InputKey[Unit] = inputKey(
    "List suppression rules added to the Owasp Engine which are defined in the project definition (ie. build.sbt), or are imported packaged suppressions."
  )
}
