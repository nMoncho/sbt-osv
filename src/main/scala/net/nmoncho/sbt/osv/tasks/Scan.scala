/*
 * Copyright 2025 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package tasks

import net.nmoncho.sbt.osv.Keys.*
import net.nmoncho.sbt.osv.settings.{EngineSettings, ReportGenerator, ScopesSettings, SummaryReport}
import sbt.*
import sbt.Keys.*
import sbt.complete.Parser
import sbt.plugins.JvmPlugin

object Scan {

  private[tasks] val argumentsParser: Parser[Seq[ParseOptions]] =
    (ListSettingsArg | SingleReportArg | AllProjectsArg | ListUnusedSuppressionsArg | OriginalSummaryArg | AllVulnerabilitiesSummaryArg | OffendingVulnerabilitiesSummaryArg).*

  private case class CheckSettings(
      name: String,
      scopes: ScopesSettings,
      failureScore: Double,
      engineSettings: EngineSettings,
      dependencies: Set[Attributed[File]],
      suppressions: Set[String],
      outputDirectory: File,
      reportFormats: Seq[ReportGenerator.Format]
  )

  def apply(): Def.Initialize[InputTask[Unit]] = Def.inputTaskDyn {
    implicit val log: Logger = streams.value.log

    val arguments              = argumentsParser.parsed
    val singleReport           = arguments.contains(ParseOptions.SingleReport)
    val allProjects            = arguments.contains(ParseOptions.AllProjects)
    val listUnusedSuppressions = arguments.contains(ParseOptions.ListUnusedSuppressions)

    val summary = arguments.find(arg =>
      arg == ParseOptions.OriginalSummary || arg == ParseOptions.AllVulnerabilitiesSummary || arg == ParseOptions.OffendingVulnerabilitiesSummary
    ) match {
      case Some(ParseOptions.AllVulnerabilitiesSummary) =>
        SummaryReport.AllVulnerabilities

      case Some(ParseOptions.OffendingVulnerabilitiesSummary) =>
        SummaryReport.OffendingVulnerabilities

      case _ => SummaryReport.Original
    }

    val dependenciesAndSuppressionsTask = Def.taskDyn {
      if (singleReport && allProjects) {
        allProjectsSettings.map(Seq(_))
      } else if (!singleReport) {
        aggregateProjectsFilter.map(_.flatten)
      } else {
        sys.error("'all-projects' argument isn't supported without the use of 'single-project'")
      }
    }

    // Don't run if this project has been configured to be skipped
    // But if it's a singleReport, then users may run the `dependencyCheckAggregate`
    if (!osvSkip.value || singleReport) {
      Def.task {
        dependenciesAndSuppressionsTask.value.foreach { checkSettings =>
          log.info(s"Running dependency check for [${checkSettings.name}]")

          val settings = checkSettings.engineSettings

          log.info("Scanning following dependencies: ")
          checkSettings.dependencies.foreach(f => log.info("\t" + f.data.getName))

//          if (arguments.contains(ParseOptions.ListSettings)) {
//            log.info(s"\nDependencyCheck settings for [${checkSettings.name}]:")
//            ListSettings(settings, checkSettings.scopes)
//          }

          withEngine(settings) { engine =>
            val failureOpt =
              try {
                analyzeProject(
                  checkSettings.name,
                  engine,
                  checkSettings.dependencies,
                  checkSettings.suppressions,
                  checkSettings.failureScore,
                  checkSettings.outputDirectory,
                  checkSettings.reportFormats,
                  summary
                )

                Option.empty[Throwable] // success
              } catch {
                case t: VulnerabilityFoundException if listUnusedSuppressions =>
                  // Hold and throw later
                  Some(t)
              }

            if (listUnusedSuppressions) {
//              val unusedSuppressions = engine
//                .getObject(SUPPRESSION_OBJECT_KEY)
//                .asInstanceOf[java.util.List[DcSuppressionRule]]
//                .asScala
//                .filter(sup => !sup.isMatched && !sup.isBase)
//
//              if (unusedSuppressions.nonEmpty) {
//                log.info(s"""
//                            |
//                            |Found [${unusedSuppressions.size}] unused suppressions for project [${checkSettings.name}]:
//                            |${unusedSuppressions.mkString("\n\t", "\n\t", "\n")}
//                            |
//                            |""".stripMargin)
//              } else {
//                log.info("No unused suppressions.")
//              }

              // We must throw an exception if it was caught to list the unused suppressions
              failureOpt.foreach(ex => throw ex)
            }
          }
        }
      } tag NonParallel
    } else {
      Def.task {
        log.info(s"Skipping dependency check for [${name.value}]")
      }
    }
  } tag NonParallel

  private lazy val allProjectsSettings: Def.Initialize[Task[CheckSettings]] = Def.task {
    CheckSettings(
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

  private lazy val aggregateProjectsFilter = Def.settingDyn {
    perProjectSettingsTask.all(ScopeFilter(inAggregates(thisProjectRef.value)))
  }

  private lazy val perProjectSettingsTask: Def.Initialize[Task[Seq[CheckSettings]]] =
    Def.taskDyn {
      if (
        !thisProject.value.autoPlugins.contains(JvmPlugin) || (osvSkip ?? false).value
      )
        Def.task(Seq.empty[CheckSettings])
      else
        Def.task(
          Seq(
            CheckSettings(
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
