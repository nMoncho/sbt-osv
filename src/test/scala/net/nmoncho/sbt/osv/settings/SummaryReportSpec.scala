/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package settings

import java.io.File

import net.nmoncho.sbt.osv.api.OsvVulnerability

class SummaryReportSpec extends munit.FunSuite {

  private val dep = Dependency("org", "artifact", "1.0", new File("artifact-1.0.jar"))

  private def osvSource(id: String): OsvVulnerability =
    OsvVulnerability(
      id            = id,
      schemaVersion = "1.0.0",
      summary       = "",
      details       = "",
      affected      = Seq.empty
    )

  private def vulnerability(id: String, scores: Vulnerability.Score*): Vulnerability =
    Vulnerability(id, Set.empty, scores.toSet, Vulnerability.FixedStatus.Unknown, osvSource(id))

  private def score(name: String, value: Double): Vulnerability.Score =
    Vulnerability.Score(name, "", value)

  // -------------------------------------------------------------------------
  // Vulnerability sort order
  // -------------------------------------------------------------------------

  test("AllVulnerabilities – vulnerabilities are sorted descending by max score") {
    val low  = vulnerability("CVE-LOW", score("CVSS_V3", 3.0))
    val high = vulnerability("CVE-HIGH", score("CVSS_V3", 9.0))

    val result = SummaryReport.AllVulnerabilities.buildSummary(
      Map(dep -> Set(high, low)),
      failCvssScore = 11.0
    )

    assert(
      result.indexOf("CVE-HIGH") < result.indexOf("CVE-LOW"),
      s"Expected CVE-HIGH before CVE-LOW but got:\n$result"
    )
  }

  test("AllVulnerabilities – equal max scores are broken by vulnerability ID") {
    val vulnA = vulnerability("CVE-A", score("CVSS_V3", 7.0))
    val vulnB = vulnerability("CVE-B", score("CVSS_V3", 7.0))

    val result = SummaryReport.AllVulnerabilities.buildSummary(
      Map(dep -> Set(vulnB, vulnA)),
      failCvssScore = 11.0
    )

    assert(
      result.indexOf("CVE-A") < result.indexOf("CVE-B"),
      s"Expected CVE-A before CVE-B (alphabetical tie-break) but got:\n$result"
    )
  }

  // -------------------------------------------------------------------------
  // Score name sort within a vulnerability line
  // -------------------------------------------------------------------------

  test("AllVulnerabilities – scores within a vulnerability are listed sorted by name") {
    val vuln = vulnerability(
      "CVE-2024-0001",
      score("CVSS_V3", 7.5),
      score("CVSS_V2", 5.0)
    )

    val result = SummaryReport.AllVulnerabilities.buildSummary(
      Map(dep -> Set(vuln)),
      failCvssScore = 11.0
    )

    assert(
      result.indexOf("CVSS_V2") < result.indexOf("CVSS_V3"),
      s"Expected CVSS_V2 before CVSS_V3 (alphabetical name sort) but got:\n$result"
    )
  }

  // -------------------------------------------------------------------------
  // OffendingVulnerabilities
  // -------------------------------------------------------------------------

  test("OffendingVulnerabilities – only failing vulnerabilities appear in the summary") {
    val failing    = vulnerability("CVE-FAIL", score("CVSS_V3", 9.0))
    val nonFailing = vulnerability("CVE-SAFE", score("CVSS_V3", 3.0))

    val result = SummaryReport.OffendingVulnerabilities.buildSummary(
      Map(dep -> Set(failing, nonFailing)),
      failCvssScore = 5.0
    )

    assert(result.contains("CVE-FAIL"), "failing vulnerability should appear in the summary")
    assert(!result.contains("CVE-SAFE"), "non-failing vulnerability should be excluded")
  }

  test("OffendingVulnerabilities – failing vulnerabilities are sorted descending by score") {
    val medFail  = vulnerability("CVE-MED", score("CVSS_V3", 6.0))
    val highFail = vulnerability("CVE-HIGH", score("CVSS_V3", 9.0))

    val result = SummaryReport.OffendingVulnerabilities.buildSummary(
      Map(dep -> Set(highFail, medFail)),
      failCvssScore = 5.0
    )

    assert(
      result.indexOf("CVE-HIGH") < result.indexOf("CVE-MED"),
      s"Expected CVE-HIGH (9.0) before CVE-MED (6.0)  but got:\n$result"
    )
  }

}
