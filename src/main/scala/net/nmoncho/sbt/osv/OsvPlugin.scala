/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import net.nmoncho.sbt.osv.settings._
import net.nmoncho.sbt.osv.tasks._
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object OsvPlugin extends AutoPlugin {

  override def requires = JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  val autoImport: net.nmoncho.sbt.osv.Keys.type = net.nmoncho.sbt.osv.Keys

  import autoImport.*

  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    osvFailBuildOnCVSS := 11.0,
    osvAnalysisTimeout := None,
    osvEngineSettings := EngineSettings.Default,
    osvSuppressions := SuppressionSettings.Default,
    osvReportFormats := Seq(ReportGenerator.HTML),
    osvScopes := ScopesSettings.Default,
    osvConnectionTimeout := None,
    osvConnectionReadTimeout := None
  )

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    osvSkip := false,
    osvScan := Scan().evaluated,
    osvListSuppressions := ListSuppressions().evaluated,
    osvListSettings := ScanSettings.listTask().evaluated,
    Compile / resourceGenerators += GenerateSuppressions.exportPackagedSuppressions(),
    osvOutputDirectory := crossTarget.value,
    osvScan / aggregate := false,
    osvListSuppressions / aggregate := false,
    osvListSettings / aggregate := false,
    Global / concurrentRestrictions += Tags.exclusive(NonParallel)
  )

}
