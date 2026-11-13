/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.settings

import java.io.File

import scala.io.Source
import scala.util.Using

import net.nmoncho.sbt.osv.Dependency
import net.nmoncho.sbt.osv.Engine.ScanResult
import net.nmoncho.sbt.osv.TestUtils
import net.nmoncho.sbt.osv.Vulnerability
import net.nmoncho.sbt.osv.api.OsvVulnerability
import net.nmoncho.sbt.osv.api.v1.V1VulnerabilityList
import sbt.Logger

class ReportGeneratorSpec extends munit.FunSuite with TestUtils {

  implicit val log: Logger = Logger.Null

  test("generate a HTML report") {
    val result = readJsonQueries()

    val model: Map[Dependency, Set[OsvVulnerability]] = result.map { case (d, vs) =>
      d -> vs.map(_.source)
    }

    sbt.IO.write(
      new File("report.html"),
      net.nmoncho.sbt.osv.html.html.report(ScanResult.sortBySeverity(result)).toString()
    )
  }

  test("HTML report is self-contained: no external scripts, details rendered server-side") {
    val dep           = Dependency("org.example", "lib", "1.2.3", new File("lib-1.2.3.jar"))
    val vulnerability = Vulnerability(
      id      = "GHSA-xxxx",
      aliases = Set.empty,
      scores  = Set.empty,
      fixed   = Vulnerability.FixedStatus.Unknown,
      source  = OsvVulnerability(
        "GHSA-xxxx",
        "1.0.0",
        "a summary",
        "This is a **serious** issue.",
        Seq.empty
      )
    )

    val html = ReportGenerator.HTML.generate(Map(dep -> Set(vulnerability)))

    assert(!html.contains("cdn.jsdelivr"), "report must not reference an external CDN")
    assert(!html.contains("<script"), "report must not load any external script")
    assert(!html.contains("<md "), "the markdown-tag element must be gone")
    assert(html.contains("vuln-details"), "details block should be present")
    // Details are rendered from markdown server-side.
    assert(html.contains("<strong>serious</strong>"), "markdown should be rendered to HTML")
  }

  test("JSON report name is osv-report.json") {
    assertEquals(ReportGenerator.JSON.reportName("anything"), "osv-report.json")
  }

  test("JSON report is valid JSON with the expected structure") {
    val dep           = Dependency("org.example", "lib", "1.2.3", new File("lib-1.2.3.jar"))
    val vulnerability = Vulnerability(
      id      = "GHSA-xxxx",
      aliases = Set("CVE-2022-1"),
      scores  = Set(
        Vulnerability.Score("CVSS_V3", "CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:N", 7.6)
      ),
      fixed  = Vulnerability.FixedStatus.Unknown,
      source = OsvVulnerability("GHSA-xxxx", "1.0.0", "a summary", "", Seq.empty)
    )

    val json   = ReportGenerator.JSON.generate(Map(dep -> Set(vulnerability)))
    val parsed = ujson.read(json)

    val d = parsed("dependencies")(0)
    assertEquals(d("coordinates").str, "org.example:lib:1.2.3")
    assertEquals(d("groupId").str, "org.example")

    val v = d("vulnerabilities")(0)
    assertEquals(v("id").str, "GHSA-xxxx")
    assertEquals(v("aliases")(0).str, "CVE-2022-1")
    assertEquals(v("summary").str, "a summary")
    assertEquals(v("scores")(0)("score").num, 7.6)
  }

  test("JSON report renders an empty dependency set as an empty array") {
    val json   = ReportGenerator.JSON.generate(Map.empty)
    val parsed = ujson.read(json)
    assertEquals(parsed("dependencies").arr.length, 0)
  }

  test("SARIF report name is osv-report.sarif") {
    assertEquals(ReportGenerator.SARIF.reportName("anything"), "osv-report.sarif")
  }

  test("SARIF report is a valid 2.1.0 document with rules, results and severity mapping") {
    val dep           = Dependency("org.example", "lib", "1.2.3", new File("lib-1.2.3.jar"))
    val vulnerability = Vulnerability(
      id      = "GHSA-xxxx",
      aliases = Set("CVE-2022-1"),
      scores  = Set(Vulnerability.Score("CVSS_V3", "CVSS:3.1/x", 9.8)),
      fixed   = Vulnerability.FixedStatus.Unknown,
      source  = OsvVulnerability("GHSA-xxxx", "1.0.0", "a summary", "details", Seq.empty)
    )

    val parsed = ujson.read(ReportGenerator.SARIF.generate(Map(dep -> Set(vulnerability))))

    assertEquals(parsed("version").str, "2.1.0")
    val run = parsed("runs")(0)
    assertEquals(run("tool")("driver")("name").str, "sbt-osv")

    val rule = run("tool")("driver")("rules")(0)
    assertEquals(rule("id").str, "GHSA-xxxx")
    assertEquals(rule("properties")("security-severity").str, "9.8")

    val res = run("results")(0)
    assertEquals(res("ruleId").str, "GHSA-xxxx")
    assertEquals(res("ruleIndex").num.toInt, 0)
    assertEquals(res("level").str, "error") // 9.8 -> error
    assert(res("message")("text").str.contains("org.example:lib:1.2.3"))
    assertEquals(
      res("locations")(0)("physicalLocation")("artifactLocation")("uri").str,
      "build.sbt"
    )
  }

  test("SARIF result carries the fixed version in message and properties") {
    val dep    = Dependency("org.example", "lib", "1.2.3", new File("lib-1.2.3.jar"))
    val source = OsvVulnerability("GHSA-fix", "1.0.0", "a summary", "details", Seq.empty)
    val fixed  = Vulnerability(
      "GHSA-fix",
      Set.empty,
      Set.empty,
      Vulnerability.FixedStatus.Fixed("1.2.4"),
      source
    )
    val unresolved = fixed.copy(id = "GHSA-open", fixed = Vulnerability.FixedStatus.NotFixed)

    val results = ujson
      .read(
        ReportGenerator.SARIF.generate(Map(dep -> Set(fixed, unresolved)))
      )("runs")(0)("results")
      .arr

    val withFix = results.find(_("ruleId").str == "GHSA-fix").get
    assert(withFix("message")("text").str.endsWith("Fixed in 1.2.4"))
    assertEquals(withFix("properties")("fixedVersion").str, "1.2.4")

    val withoutFix = results.find(_("ruleId").str == "GHSA-open").get
    assert(!withoutFix("message")("text").str.contains("Fixed in"))
    assert(!withoutFix.obj.contains("properties"))
  }

  test("SARIF deduplicates a shared advisory into one rule with a result per dependency") {
    val depA   = Dependency("org.a", "a", "1.0", new File("a-1.0.jar"))
    val depB   = Dependency("org.b", "b", "2.0", new File("b-2.0.jar"))
    val shared = Vulnerability(
      id      = "GHSA-shared",
      aliases = Set.empty,
      scores  = Set(Vulnerability.Score("CVSS_V3", "CVSS:3.1/x", 5.0)),
      fixed   = Vulnerability.FixedStatus.Unknown,
      source  = OsvVulnerability("GHSA-shared", "1.0.0", "", "", Seq.empty)
    )

    val run = ujson.read(
      ReportGenerator.SARIF.generate(Map(depA -> Set(shared), depB -> Set(shared)))
    )("runs")(0)

    assertEquals(run("tool")("driver")("rules").arr.length, 1)
    assertEquals(run("results").arr.length, 2)
    // 5.0 -> warning
    assertEquals(run("results")(0)("level").str, "warning")
  }

  test("SARIF report is well-formed for an empty result") {
    val parsed = ujson.read(ReportGenerator.SARIF.generate(Map.empty))
    assertEquals(parsed("version").str, "2.1.0")
    assertEquals(parsed("runs")(0)("results").arr.length, 0)
    assertEquals(parsed("runs")(0)("tool")("driver")("rules").arr.length, 0)
  }

  @SuppressWarnings(Array("DisableSyntax.noValPatterns"))
  private def readJsonQueries(): Map[Dependency, Set[Vulnerability]] = {
    import net.nmoncho.sbt.osv.api.SnakeCaseConfig.*

    val folder = new File("src/test/resources/queries")
    folder
      .listFiles()
      .map { f =>
        val List(org, art, ver) = f.getName.replace(".json", "").split("_").toList
        val d                   = Dependency(org, art, ver, f)

        val jsonText = Using(Source.fromFile(f)) { x =>
          x.getLines().mkString("\n")
        }.get

        d -> read[V1VulnerabilityList](jsonText).vulnerabilities(d)
      }
      .toMap
  }
}
