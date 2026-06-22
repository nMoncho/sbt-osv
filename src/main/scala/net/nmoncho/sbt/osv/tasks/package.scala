package net.nmoncho.sbt.osv

import net.nmoncho.sbt.osv.settings.{EngineSettings, ReportGenerator, SummaryReport}

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal
import sbt.Tags.Tag
import sbt.*
import sbt.complete.DefaultParsers.*
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
    case object ListUnusedSuppressions extends ParseOptions

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
  private[tasks] val ListUnusedSuppressionsArg =
    (Space ~> token("list-unused-suppressions")) ^^^ ParseOptions.ListUnusedSuppressions

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
      case NonFatal(e) =>
        logFailure(e)
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
      suppressionRules: Set[String],
      failCvssScore: Double,
      outputDir: File,
      reportFormats: Seq[ReportGenerator.Format],
      summaryReport: SummaryReport
  )(implicit log: Logger): Unit = {
    val result = engine.analyzeDependencies(getDependencies(dependencies), suppressionRules)

    if (reportFormats.isEmpty) {
      log.info("No Report Format was selected for the Dependency Check Analysis")
    } else {
      log.info(s"Writing Dependency Check reports to [${outputDir.getAbsolutePath}]")
      reportFormats.foreach(reportFormat =>
        engine.writeReports(
          projectName,
          outputDir,
          reportFormat.name()
        )
      )
    }

    failOnFoundVulnerabilities(failCvssScore, result, projectName, summaryReport)
  }

  private def getDependencies(checkClasspath: Set[Attributed[File]])(implicit log: Logger): Set[Dependency] =
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
      analysisResult: Map[Dependency, Set[Vulnerability]],
      name: String,
      summaryReport: SummaryReport
  )(implicit log: Logger): Unit = {
    import scala.jdk.CollectionConverters.*

    val hasFailingVulnerabilities = analysisResult.exists { case (dep, vulnerabilities) =>
      vulnerabilities.exists(failingVulnerability(_, failCvssScore))
    }

    if (hasFailingVulnerabilities) {
      SummaryReport.showSummary(name, analysisResult, failCvssScore, summaryReport)

      throw new VulnerabilityFoundException(
        s"Vulnerability with CVSS score higher than [$failCvssScore] found"
      )
    }
  }

}
