/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package settings

import net.nmoncho.sbt.osv.Vulnerability.FixedStatus

/** Trait used to define how Summaries are generated after an analysis
  */
trait SummaryReport {

  /** Builds an analysis summary using the scanned dependencies and the failing CVSS Score
    *
    * @param dependencies  scanned dependencies
    * @param failCvssScore failing CVSS Score
    * @return
    */
  def buildSummary(
      analysisResult: Map[Dependency, Set[Vulnerability]],
      failCvssScore: Double
  ): String

}

object SummaryReport {

  private final val NewLine = System.getProperty("line.separator", "\n").intern()

  /** Highest-ranked score of a vulnerability, or `None` when it carries no score. */
  private def topScore(v: Vulnerability): Option[Vulnerability.Score] =
    if (v.scores.isEmpty) None else Some(v.scores.max)

  private implicit val ordering: Ordering[Vulnerability] = (x: Vulnerability, y: Vulnerability) => {
    // Scoreless vulnerabilities sort last, so scored findings surface first.
    val comp = (topScore(x), topScore(y)) match {
      case (Some(xScore), Some(yScore)) =>
        Vulnerability.Score.descendingOrder.compare(xScore, yScore)
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case (None, None) => 0
    }

    if (comp == 0) {
      x.id.compareTo(y.id)
    } else {
      comp
    }
  }

  /** Processes a vulnerability to include in a report
    *
    * If it's a failing vulnerability, it will output the line in yellow.
    *
    * @param v vulnerability to report
    * @param failCvssScore failing score
    * @return vulnerability report line
    */
  private def processVulnerability(v: Vulnerability, failCvssScore: Double): String = {
    val score =
      v.scores.toSeq.sortBy(_.name).map(s => s"${s.name} ${s.score}").mkString("(", ", ", ")")

    val fixed = v.fixed match {
      case FixedStatus.Fixed(value) => s"Fixed in $value"
      case _ => ""
    }

    if (v.failing(failCvssScore)) {
      s"${scala.Console.YELLOW}${v.id}${scala.Console.RESET} ${scala.Console.YELLOW}$score. $fixed${scala.Console.RESET}"
    } else {
      s"${v.id} $score. $fixed"
    }
  }

  /** Processes dependencies to be included in the report
    *
    * @param analysisResult      scanned dependencies
    * @param reportVulnerability predicate to define if a [[Vulnerability]] should be shown or not in the summary
    * @param summary             [[StringBuilder]] used for aggregating the report
    */
  private def dependencyProcessor(
      analysisResult: Map[Dependency, Set[Vulnerability]],
      reportVulnerability: (Vulnerability, Double) => Boolean,
      failCvssScore: Double,
      summary: StringBuilder
  )(implicit ord: Ordering[Double]): Unit = {
    // Sort dependencies by vulnerability's score in descending order
    // So dependency with the highest vulnerability shows first
    val inDescendingOrder = analysisResult.toSeq
      .sortBy { case (_, vulnerabilities) =>
        vulnerabilities.flatMap(topScore).map(_.score).headOption.getOrElse(0.0)
      }(ord.reverse)

    inDescendingOrder
      .foreach { case (dependency, vulnerabilities) =>
        if (vulnerabilities.nonEmpty) {
          // Sort vulnerabilities by score in descending order
          // So vulnerability with the highest score shows first
          val formattedVulnerabilities = vulnerabilities.toSeq.sorted.collect {
            case v if reportVulnerability(v, failCvssScore) =>
              processVulnerability(v, failCvssScore)
          }

          if (formattedVulnerabilities.nonEmpty) {
            summary
              .append(dependency.file.getName)
              .append(": ")

            summary.append(
              formattedVulnerabilities.mkString(", ")
            )

            summary.append(NewLine)
          }
        }
      }
  }

  /** DependencyCheck-style summary: lists each dependency's coordinates and the ids of
    * the vulnerabilities found for it.
    */
  object DependencyCheck extends SummaryReport {

    override def buildSummary(
        analysisResult: Map[Dependency, Set[Vulnerability]],
        failCvssScore: Double
    ): String = {
      val summary = new StringBuilder

      analysisResult.foreach { case (dependency, vulnerabilities) =>
        val ids = vulnerabilities.map(_.id).mkString(", ")

        if (ids.nonEmpty) {
          summary
            .append(dependency.coordinates)
            .append(" (")
            .append(dependency.file.getName)
            .append("): ")
            .append(ids)
            .append(NewLine)
        }
      }

      summary.toString()
    }

  }

  /** Shows all vulnerabilities in the summary with their corresponding score, whether they are an
    * offending vulnerability or not.
    */
  object AllVulnerabilities extends SummaryReport {

    override def buildSummary(
        analysisResult: Map[Dependency, Set[Vulnerability]],
        failCvssScore: Double
    ): String = {
      val summary = new StringBuilder

      dependencyProcessor(analysisResult, (_, _) => true, failCvssScore, summary)

      summary.toString()
    }

  }

  /** Shows only offending vulnerabilities in the summary with their corresponding score.
    */
  object OffendingVulnerabilities extends SummaryReport {

    override def buildSummary(
        analysisResult: Map[Dependency, Set[Vulnerability]],
        failCvssScore: Double
    ): String = {
      val summary = new StringBuilder

      dependencyProcessor(
        analysisResult,
        _.failing(_),
        failCvssScore,
        summary
      )

      summary.toString()
    }

  }

}
