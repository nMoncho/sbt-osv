/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.settings

import net.nmoncho.sbt.osv.Dependency
import net.nmoncho.sbt.osv.Engine.ScanResult
import net.nmoncho.sbt.osv.Vulnerability

sealed trait ReportGenerator {

  /** Report filename based on the project name
    */
  def reportName(projectName: String): String

  /** Generates a vulnerability report based on findings
    *
    * @param result the found vulnerabilities for each dependency
    * @return the report as a string
    */
  def generate(result: Map[Dependency, Set[Vulnerability]]): String
}

object ReportGenerator {

  case object HTML extends ReportGenerator {

    override def reportName(projectName: String): String = "osv-report.html"

    override def generate(result: Map[Dependency, Set[Vulnerability]]): String =
      net.nmoncho.sbt.osv.html.html.report(ScanResult.sortBySeverity(result)).toString()
  }

  /** Machine-readable JSON report, suitable for CI pipelines and downstream tooling. */
  case object JSON extends ReportGenerator {

    override def reportName(projectName: String): String = "osv-report.json"

    override def generate(result: Map[Dependency, Set[Vulnerability]]): String = {
      val dependencies = result.toSeq
        .sortBy(_._1.coordinates)
        .map { case (dependency, vulnerabilities) =>
          ujson.Obj(
            "coordinates" -> ujson.Str(dependency.coordinates),
            "groupId" -> ujson.Str(dependency.groupId),
            "artifactId" -> ujson.Str(dependency.artifactId),
            "version" -> ujson.Str(dependency.revision),
            "purl" -> ujson.Str(dependency.purl),
            "file" -> ujson.Str(dependency.file.getName),
            "vulnerabilities" -> ujson.Arr(
              vulnerabilities.toSeq.sortBy(_.id).map { vulnerability =>
                ujson.Obj(
                  "id" -> ujson.Str(vulnerability.id),
                  "aliases" -> ujson.Arr(vulnerability.aliases.toSeq.sorted.map(ujson.Str(_)): _*),
                  "summary" -> ujson.Str(vulnerability.source.summary),
                  "fixStatus" -> (vulnerability.fixed match {
                    case Vulnerability.FixedStatus.Fixed(value) => ujson.Str(value)
                    case _ => ujson.Null
                  }),
                  "scores" -> ujson.Arr(
                    vulnerability.scores.toSeq.sortBy(_.name).map { score =>
                      ujson.Obj(
                        "type" -> ujson.Str(score.name),
                        "vector" -> ujson.Str(score.vector),
                        "score" -> ujson.Num(score.score),
                        "severity" -> ujson.Str(Vulnerability.Score.scoreSeverity(score.vector))
                      )
                    }: _*
                  ),
                  "references" -> ujson.Arr(
                    vulnerability.source.references
                      .getOrElse(Seq.empty)
                      .map(reference => ujson.Str(reference.url)): _*
                  )
                )
              }: _*
            )
          )
        }

      ujson.write(ujson.Obj("dependencies" -> ujson.Arr(dependencies: _*)), indent = 2)
    }
  }

  /** SARIF 2.1.0 report, the format consumed by GitHub / GitLab code scanning. */
  case object SARIF extends ReportGenerator {

    private final val InformationUri = "https://github.com/nMoncho/sbt-osv"

    override def reportName(projectName: String): String = "osv-report.sarif"

    override def generate(result: Map[Dependency, Set[Vulnerability]]): String = {
      // One SARIF rule per distinct advisory id (an advisory may affect many
      // dependencies); results reference the rule by index.
      val distinctVulns: Seq[Vulnerability] =
        result.values.flatten.groupBy(_.id).toSeq.sortBy(_._1).map(_._2.head)

      val ruleIndex: Map[String, Int] = distinctVulns.map(_.id).zipWithIndex.toMap

      val rules = distinctVulns.map(rule)

      val results = result.toSeq
        .sortBy(_._1.coordinates)
        .flatMap { case (dependency, vulnerabilities) =>
          vulnerabilities.toSeq
            .sortBy(_.id)
            .map(vulnerability =>
              sarifResult(dependency, vulnerability, ruleIndex(vulnerability.id))
            )
        }

      val document = ujson.Obj(
        "$schema" -> ujson.Str("https://json.schemastore.org/sarif-2.1.0.json"),
        "version" -> ujson.Str("2.1.0"),
        "runs" -> ujson.Arr(
          ujson.Obj(
            "tool" -> ujson.Obj(
              "driver" -> ujson.Obj(
                "name" -> ujson.Str("sbt-osv"),
                "informationUri" -> ujson.Str(InformationUri),
                "rules" -> ujson.Arr(rules: _*)
              )
            ),
            "results" -> ujson.Arr(results: _*)
          )
        )
      )

      ujson.write(document, indent = 2)
    }

    private def maxScore(vulnerability: Vulnerability): Option[Double] =
      if (vulnerability.scores.isEmpty) None else Some(vulnerability.scores.map(_.score).max)

    /** Maps CVSS severity to a SARIF result level (independent of the build's fail
      * threshold, which `generate` does not receive).
      */
    private def levelFor(vulnerability: Vulnerability): String =
      maxScore(vulnerability) match {
        case Some(score) if score >= 7.0 => "error"
        case Some(score) if score >= 4.0 => "warning"
        case _ => "note"
      }

    private def rule(vulnerability: Vulnerability): ujson.Obj = {
      val shortText =
        if (vulnerability.source.summary.nonEmpty) vulnerability.source.summary
        else vulnerability.id

      val properties = ujson.Obj(
        "tags" -> ujson.Arr(ujson.Str("security"), ujson.Str("vulnerability"))
      )
      if (vulnerability.aliases.nonEmpty) {
        properties("aliases") = ujson.Arr(vulnerability.aliases.toSeq.sorted.map(ujson.Str(_)): _*)
      }
      // GitHub maps `security-severity` (a CVSS number as a string) to its own levels.
      maxScore(vulnerability).foreach(score =>
        properties("security-severity") = ujson.Str(score.toString)
      )

      val descriptor = ujson.Obj(
        "id" -> ujson.Str(vulnerability.id),
        "name" -> ujson.Str(vulnerability.id),
        "shortDescription" -> ujson.Obj("text" -> ujson.Str(shortText)),
        "properties" -> properties
      )
      if (vulnerability.source.details.nonEmpty) {
        descriptor("fullDescription") = ujson.Obj("text" -> ujson.Str(vulnerability.source.details))
      }
      vulnerability.source.references
        .getOrElse(Seq.empty)
        .headOption
        .foreach(reference => descriptor("helpUri") = ujson.Str(reference.url))

      descriptor
    }

    private def sarifResult(
        dependency: Dependency,
        vulnerability: Vulnerability,
        index: Int
    ): ujson.Obj = {
      val aliasText =
        if (vulnerability.aliases.nonEmpty)
          vulnerability.aliases.toSeq.sorted.mkString(" (", ", ", ")")
        else ""

      // SARIF has no field for a fixed version: `fixes` needs file edits. Put it in
      // the message (shown by code scanning UIs) and the property bag (for tools).
      val fixedVersion = vulnerability.fixed match {
        case Vulnerability.FixedStatus.Fixed(version) => Some(version)
        case _ => None
      }
      val fixedText = fixedVersion.fold("")(version => s". Fixed in $version")

      val result = ujson.Obj(
        "ruleId" -> ujson.Str(vulnerability.id),
        "ruleIndex" -> ujson.Num(index.toDouble),
        "level" -> ujson.Str(levelFor(vulnerability)),
        "message" -> ujson.Obj(
          "text" -> ujson.Str(
            s"${dependency.coordinates} is affected by ${vulnerability.id}$aliasText$fixedText"
          )
        ),
        "locations" -> ujson.Arr(
          ujson.Obj(
            "physicalLocation" -> ujson.Obj(
              "artifactLocation" -> ujson.Obj("uri" -> ujson.Str("build.sbt"))
            ),
            "logicalLocations" -> ujson.Arr(
              ujson.Obj(
                "fullyQualifiedName" -> ujson.Str(dependency.coordinates),
                "kind" -> ujson.Str("module")
              )
            )
          )
        ),
        "partialFingerprints" -> ujson.Obj(
          "packageVulnerability" -> ujson.Str(s"${dependency.purl}/${vulnerability.id}")
        )
      )

      fixedVersion.foreach(version =>
        result("properties") = ujson.Obj("fixedVersion" -> ujson.Str(version))
      )

      result
    }
  }

}
