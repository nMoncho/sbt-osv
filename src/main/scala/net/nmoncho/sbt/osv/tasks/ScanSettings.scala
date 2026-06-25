/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.tasks

import net.nmoncho.sbt.osv.Keys._
import net.nmoncho.sbt.osv.SuppressionRule
import net.nmoncho.sbt.osv.settings.EngineSettings
import net.nmoncho.sbt.osv.settings.ReportGenerator
import net.nmoncho.sbt.osv.settings.ScopesSettings
import sbt.Def
import sbt.Keys.name
import sbt.Keys.streams
import sbt.Keys.thisProject
import sbt.Keys.thisProjectRef
import sbt._
import sbt.plugins.JvmPlugin

object ScanSettings {

  private[tasks] case class Settings(
      name: String,
      scopes: ScopesSettings,
      failureScore: Double,
      engineSettings: EngineSettings,
      dependencies: Set[Attributed[File]],
      suppressions: Set[SuppressionRule],
      outputDirectory: File,
      reportFormats: Seq[ReportGenerator]
  )

  def listTask(): Def.Initialize[InputTask[Unit]] = Def.inputTaskDyn {
    Def.task {
      implicit val log: Logger = streams.value.log
      val settings             = projectSelectionParser.parsed match {
        case Some(ProjectSelection.AllProjects) =>
          Seq(allProjectsSettings.value)

        case Some(ProjectSelection.PerProject) | _ =>
          aggregateProjectsFilter.value.flatten
      }

      settings.foreach(s => apply(s))
    }
  }

  def apply(settings: Settings)(implicit log: Logger): Unit = {
    log.info(s"\nOSV scan settings for [${settings.name}]:")
    log.info(settings.scopes.toPrettyString().split('\n').mkString("\t", "\n\t", ""))
    log.info(settings.engineSettings.toPrettyString().split('\n').mkString("\t", "\n\t", ""))
    log.info(s"\tOutput directory: ${settings.outputDirectory.getAbsolutePath}")
    log.info(s"\tFail on CVSS score: ${settings.failureScore}")
  }

  private[tasks] lazy val allProjectsSettings: Def.Initialize[Task[Settings]] =
    Def.task {
      Settings(
        name.value,
        osvScopes.value,
        osvFailBuildOnCVSS.value,
        osvEngineSettings.value,
        AllProjectsScan.dependencies().value,
        AllProjectsScan.suppressions().value,
        osvOutputDirectory.value,
        osvReportFormats.value
      )
    }

  private[tasks] lazy val aggregateProjectsFilter = Def.settingDyn {
    perProjectSettingsTask.all(ScopeFilter(inAggregates(thisProjectRef.value)))
  }

  private[tasks] lazy val perProjectSettingsTask: Def.Initialize[Task[Seq[Settings]]] =
    Def.taskDyn {
      if (!thisProject.value.autoPlugins.contains(JvmPlugin) || (osvSkip ?? false).value)
        Def.task(Seq.empty[Settings])
      else
        Def.task(
          Seq(
            Settings(
              name.value,
              osvScopes.value,
              osvFailBuildOnCVSS.value,
              osvEngineSettings.value,
              Dependencies.projectDependencies.value,
              GenerateSuppressions.forProject.value,
              osvOutputDirectory.value,
              osvReportFormats.value
            )
          )
        )
    }
}
