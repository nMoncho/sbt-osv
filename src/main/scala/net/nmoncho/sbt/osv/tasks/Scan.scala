/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package tasks

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import net.nmoncho.sbt.osv.Keys._
import net.nmoncho.sbt.osv.settings.SummaryReport
import sbt.Keys._
import sbt._
import sbt.complete.Parser

object Scan {

  private[tasks] val argumentsParser: Parser[Seq[ParseOptions]] =
    (ListSettingsArg | SingleReportArg | AllProjectsArg | ListUnusedSuppressionsArg | OriginalSummaryArg | AllVulnerabilitiesSummaryArg | OffendingVulnerabilitiesSummaryArg).*

  def apply(): Def.Initialize[InputTask[Unit]] = Def
    .inputTaskDyn {
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

        case _ =>
          SummaryReport.DependencyCheck
      }

      val dependenciesAndSuppressionsTask = Def.taskDyn {
        if (singleReport && allProjects) {
          ScanSettings.allProjectsSettings.map(Seq(_))
        } else if (!singleReport) {
          ScanSettings.aggregateProjectsFilter.map(_.flatten)
        } else {
          sys.error("'single-reports' argument isn't supported without the use of 'all-projects'")
        }
      }

      // Don't run if this project has been configured to be skipped
      // But if it's a singleReport, then users may run on aggregate
      if (!osvSkip.value || singleReport) {
        Def
          .task {
            dependenciesAndSuppressionsTask.value.foreach { checkSettings =>
              log.info(s"Running dependency check for [${checkSettings.name}]")

              val settings = checkSettings.engineSettings

              if (arguments.contains(ParseOptions.ListSettings)) {
                ScanSettings(checkSettings)
              }

              withEngine(settings) { engine =>
                Try {
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
                } match {
                  case Success(result) if listUnusedSuppressions =>
                    logUnusedSuppression(checkSettings.name, result.unusedSuppressions)

                  case Failure(found: VulnerabilityFoundException) if listUnusedSuppressions =>
                    logUnusedSuppression(checkSettings.name, found.scanResult.unusedSuppressions)
                    throw found

                  case Failure(t) => throw t

                  case Success(_) =>
                    log.info(s"No offending vulnerabilities found for [${checkSettings.name}]")
                }
              }
            }
          }
          .tag(NonParallel)
      } else {
        Def.task {
          log.info(s"Skipping dependency check for [${name.value}]")
        }
      }
    }
    .tag(NonParallel)

  private def logUnusedSuppression(projectName: String, unusedSuppressions: Set[SuppressionRule])(
      implicit log: Logger
  ): Unit =
    if (unusedSuppressions.nonEmpty) {
      log.info(s"""
                  |
                  |Found [${unusedSuppressions.size}] unused suppressions for project [${projectName}]:
                  |${unusedSuppressions.mkString("\n\t", "\n\t", "\n")}
                  |
                  |""".stripMargin)
    } else {
      log.info("No unused suppressions.")
    }
}
