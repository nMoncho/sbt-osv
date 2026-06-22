/*
 * Copyright 2025 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import net.nmoncho.sbt.osv.settings.*
import net.nmoncho.sbt.osv.tasks.*
import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin

import java.text.Format

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
    osvReportFormats := Seq.empty,
    osvScopes := ScopesSettings.Default,
    osvProxy := ProxySettings.Default,
    osvConnectionTimeout := None,
    osvConnectionReadTimeout := None
  )

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    osvSkip := false,
    osvScan := osvTask.evaluated,
    osvListSuppressions := ListSuppressions().evaluated,
    Compile / resourceGenerators += GenerateSuppressions.exportPackagedSuppressions(),
    osvOutputDirectory := crossTarget.value,
    osvScan / aggregate := false,
    osvListSuppressions / aggregate := false,
    Global / concurrentRestrictions += Tags.exclusive(NonParallel)
  )

  private def osvTask: Def.Initialize[InputTask[Unit]] = Scan()

}
