/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.settings

import net.nmoncho.sbt.osv.Dependency
import net.nmoncho.sbt.osv.Vulnerability
import net.nmoncho.sbt.osv.api.OsvVulnerability

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

    override def generate(result: Map[Dependency, Set[Vulnerability]]): String = {
      val model: Map[Dependency, Set[OsvVulnerability]] = result.map { case (d, vs) =>
        d -> vs.map(_.source)
      }

      net.nmoncho.sbt.osv.html.html.report(model).toString()
    }
  }

}
