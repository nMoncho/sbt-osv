/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import scala.util.control.NonFatal

import net.nmoncho.sbt.osv.settings.EngineSettings
import net.nmoncho.sbt.osv.settings.ReportGenerator
import net.nmoncho.sbt.osv.settings.SummaryReport
import sbt.Tags.Tag
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser
import xsbti.FileConverter

package object tasks {

  private[tasks] sealed abstract class ParseResult extends Product with Serializable

  private[tasks] sealed abstract class ProjectSelection extends ParseResult
  private[tasks] object ProjectSelection {
    case object PerProject extends ProjectSelection
    case object AllProjects extends ProjectSelection
  }

  private[tasks] sealed abstract class ParseOptions extends ParseResult
  private[tasks] object ParseOptions {
    case object ListSettings extends ParseOptions
    case object SingleReport extends ParseOptions
    case object AllProjects extends ParseOptions
    case object ShowSuppressed extends ParseOptions
    case object ListUnusedSuppressions extends ParseOptions
    case object FailOnUnusedSuppressions extends ParseOptions

    case object OriginalSummary extends ParseOptions
    case object AllVulnerabilitiesSummary extends ParseOptions
    case object OffendingVulnerabilitiesSummary extends ParseOptions
  }

  private[tasks] val PerProject  = (Space ~> token("per-project")) ^^^ ProjectSelection.PerProject
  private[tasks] val AllProjects = (Space ~> token("all-projects")) ^^^ ProjectSelection.AllProjects

  private[tasks] val ListSettingsArg =
    (Space ~> token("list-settings")) ^^^ ParseOptions.ListSettings
  private[tasks] val SingleReportArg =
    (Space ~> token("single-report")) ^^^ ParseOptions.SingleReport
  private[tasks] val AllProjectsArg =
    (Space ~> token("all-projects")) ^^^ ParseOptions.AllProjects
  private[tasks] val ShowSuppressedArg =
    (Space ~> token("show-suppressed")) ^^^ ParseOptions.ShowSuppressed
  private[tasks] val ListUnusedSuppressionsArg =
    (Space ~> token("list-unused-suppressions")) ^^^ ParseOptions.ListUnusedSuppressions
  private[tasks] val FailOnUnusedSuppressionsArg =
    (Space ~> token("fail-on-unused-suppressions")) ^^^ ParseOptions.FailOnUnusedSuppressions

  private[tasks] val OriginalSummaryArg =
    (Space ~> token("original-summary")) ^^^ ParseOptions.OriginalSummary
  private[tasks] val AllVulnerabilitiesSummaryArg =
    (Space ~> token("all-vulnerabilities-summary")) ^^^ ParseOptions.AllVulnerabilitiesSummary
  private[tasks] val OffendingVulnerabilitiesSummaryArg =
    (Space ~> token(
      "offending-vulnerabilities-summary"
    )) ^^^ ParseOptions.OffendingVulnerabilitiesSummary

  private[tasks] val projectSelectionParser: Parser[Option[ParseResult]] =
    (PerProject | AllProjects).?

  val NonParallel: Tag = Tags.Tag("NonParallel")

  def withEngine[A](settings: EngineSettings)(fn: Engine => A)(implicit log: Logger): A = {
    val engine: Engine = Engine.create(settings)

    try {
      fn(engine)
    } catch {
      case NonFatal(e: VulnerabilityFoundException) =>
        log.error(s"${e.getLocalizedMessage}")
        logThrowable(e)
        throw e

      case NonFatal(e) =>
        log.error(s"Failed running OSV scan: ${e.getLocalizedMessage}")
        logThrowable(e)
        throw e
    } finally {
      engine.close()
    }
  }

  def logAddDependencies(
      classpath: sbt.Def.Classpath,
      configuration: Configuration
  )(implicit log: Logger, converter: FileConverter): Seq[Attributed[File]] = {
    import sbtcompat.PluginCompat.*

    logDependencies(classpath.map(_.map(toFile)), configuration, "Adding")
  }

  def logRemoveDependencies(
      classpath: sbt.Def.Classpath,
      configuration: Configuration
  )(implicit log: Logger, converter: FileConverter): Seq[Attributed[File]] = {
    import sbtcompat.PluginCompat.*

    logDependencies(classpath.map(_.map(toFile)), configuration, "Removing")
  }

  def logDependencies(
      classpath: Seq[Attributed[File]],
      configuration: Configuration,
      action: String
  )(implicit log: Logger): Seq[Attributed[File]] = {
    log.debug(s"$action ${configuration.name} dependencies to check.")
    classpath.foreach(f => log.debug("\t" + f.data.getName))
    classpath
  }

  def analyzeProject(
      projectName: String,
      engine: Engine,
      dependencies: Set[Attributed[File]],
      suppressionRules: Set[SuppressionRule],
      failCvssScore: Double,
      outputDir: File,
      reportFormats: Seq[ReportGenerator],
      summaryReport: SummaryReport
  )(implicit log: Logger): Engine.ScanResult = {
    val deps = getDependencies(dependencies)

    log.info("Scanning following dependencies: ")
    deps.toSeq
      .sortBy(_.coordinates)
      .foreach(d => log.info(s"\t ${d.coordinates} (${d.file.getName})"))

    log.info(s"Adding [${suppressionRules.size}] suppression rules to scan")

    val result = engine.analyzeDependencies(failCvssScore, deps, suppressionRules)

    if (reportFormats.isEmpty) {
      log.info("No Report Format was selected for the OSV Analysis")
    } else {
      reportFormats.foreach { reportFormat =>
        val reportName = reportFormat.reportName(projectName)
        engine.writeReports(reportName, outputDir, reportFormat.generate(result.vulnerabilities))
        log.info(s"Wrote OSV report to [${new File(outputDir, reportName).getAbsolutePath}]")
      }
    }

    // Always surface what was found, whether or not the build is going to fail, so a
    // default (never-fail) run does not look like a clean bill of health.
    reportFindings(projectName, result, failCvssScore, summaryReport)

    failOnFoundVulnerabilities(failCvssScore, result)

    result
  }

  /** Logs a findings summary after every scan, independent of the fail decision.
    * Distinguishes "no vulnerabilities found" from "found, none above the threshold"
    * so a passing run never reads as a clean bill of health when findings exist.
    */
  private def reportFindings(
      name: String,
      scanResult: Engine.ScanResult,
      failCvssScore: Double,
      summaryReport: SummaryReport
  )(implicit log: Logger): Unit = {
    val total     = scanResult.vulnerabilities.valuesIterator.map(_.size).sum
    val offending =
      scanResult.vulnerabilities.valuesIterator.flatten.count(_.failing(failCvssScore))

    if (total == 0) {
      log.info(s"No known vulnerabilities found for [$name].")
    } else {
      val summary = summaryReport.buildSummary(scanResult.vulnerabilities, failCvssScore)
      val header  =
        if (offending > 0)
          s"Found [$total] known vulnerabilities for [$name], [$offending] of which exceed the CVSS threshold of [$failCvssScore]:"
        else
          s"Found [$total] known vulnerabilities for [$name] (none exceed the CVSS threshold of [$failCvssScore]):"

      log.warn(s"\n\n$header\n\n$summary\nSee the OSV report for more details.\n")
    }
  }

  private def getDependencies(
      checkClasspath: Set[Attributed[File]]
  )(implicit log: Logger): Set[Dependency] =
    checkClasspath.flatMap(attributed =>
      if (attributed.data != null) {
        import sbtcompat.PluginCompat._

        attributed.get(moduleIDStr).map(parseModuleIDStrAttribute).map { moduleId =>
          Dependency(
            moduleId.organization,
            moduleId.name,
            moduleId.revision,
            attributed.data
          )
        }
      } else {
        // I don't think this can be `null`, but lifting it from previous plugin
        log.warn(s"Attributed[File] = [$attributed] has null data and won't be scanned")
        None
      }
    )

  private def failOnFoundVulnerabilities(
      failCvssScore: Double,
      scanResult: Engine.ScanResult
  ): Unit = {
    val hasFailingVulnerabilities = scanResult.vulnerabilities.exists { case (_, vulnerabilities) =>
      vulnerabilities.exists(_.failing(failCvssScore))
    }

    if (hasFailingVulnerabilities) {
      throw new VulnerabilityFoundException(
        s"Vulnerability with CVSS score higher than [$failCvssScore] found",
        scanResult
      )
    }
  }

}
