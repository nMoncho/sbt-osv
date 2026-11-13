/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.io.File

import net.nmoncho.sbt.osv.Engine.ScanResult
import net.nmoncho.sbt.osv.api.OsvVulnerability

class ScanResultSpec extends munit.FunSuite {

  private def dep(name: String): Dependency =
    Dependency("org.example", name, "1.0", new File(s"$name-1.0.jar"))

  private def vuln(id: String, scores: Double*): Vulnerability =
    Vulnerability(
      id      = id,
      aliases = Set.empty,
      scores  = scores.map(s => Vulnerability.Score(s"$id-$s", "", s)).toSet,
      fixed   = Vulnerability.FixedStatus.Unknown,
      source  = OsvVulnerability(id, "1.0.0", "", "", Seq.empty)
    )

  private def depOrder(input: Map[Dependency, Set[Vulnerability]]): Seq[String] =
    ScanResult.sortBySeverity(input).map(_._1.artifactId)

  test("sortBySeverity – most severe dependency first") {
    val input = Map(
      dep("low") -> Set(vuln("L", 2.0)),
      dep("high") -> Set(vuln("H", 9.0)),
      dep("mid") -> Set(vuln("M", 5.0))
    )

    assertEquals(depOrder(input), Seq("high", "mid", "low"))
  }

  test("sortBySeverity – ranks a dependency by its worst vulnerability") {
    // "mixed" has a 9.8 next to a 1.0; it must outrank "steady" (max 6.0).
    val input = Map(
      dep("steady") -> Set(vuln("S", 6.0)),
      dep("mixed") -> Set(vuln("A", 1.0), vuln("B", 9.8))
    )

    assertEquals(depOrder(input), Seq("mixed", "steady"))
  }

  test("sortBySeverity – ranks by the max score within one vulnerability") {
    val input = Map(
      dep("steady") -> Set(vuln("S", 6.0)),
      dep("spread") -> Set(vuln("X", 1.0, 9.8))
    )

    assertEquals(depOrder(input), Seq("spread", "steady"))
  }

  test("sortBySeverity – unscored dependencies go last") {
    val input = Map(
      dep("unscored") -> Set(vuln("U")),
      dep("scored") -> Set(vuln("S", 3.0))
    )

    assertEquals(depOrder(input), Seq("scored", "unscored"))
  }

  test("sortBySeverity – vulnerabilities within a dependency are most severe first") {
    val input = Map(
      dep("lib") -> Set(vuln("low", 2.0), vuln("none"), vuln("high", 9.0), vuln("mid", 5.0))
    )

    val vulnIds = ScanResult.sortBySeverity(input).head._2.map(_.id)

    assertEquals(vulnIds, Seq("high", "mid", "low", "none"))
  }

  test("sortBySeverity – keeps every vulnerability") {
    // Equal-scored vulnerabilities must not be collapsed.
    val input = Map(dep("lib") -> Set(vuln("a", 5.0), vuln("b", 5.0), vuln("c")))

    assertEquals(ScanResult.sortBySeverity(input).head._2.size, 3)
  }

  test("sortBySeverity – empty input yields empty output") {
    assertEquals(ScanResult.sortBySeverity(Map.empty), Seq.empty)
  }

}
