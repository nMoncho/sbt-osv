/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.io.File

import scala.io.Source
import scala.util.Using

import net.nmoncho.sbt.osv.api.v1.V1VulnerabilityList
import sbt.Logger

/** Deterministic, fixture-driven assertions on the JSON -> Vulnerability classification
  * and CVSS scoring, using the recorded OSV responses under `src/test/resources/queries`.
  */
class FixtureClassificationSpec extends munit.FunSuite {

  import net.nmoncho.sbt.osv.api.SnakeCaseConfig._

  implicit val log: Logger = Logger.Null

  private def classify(fixtureFileName: String): Set[Vulnerability] = {
    val file = new File(s"src/test/resources/queries/$fixtureFileName")
    val text = Using(Source.fromFile(file))(_.getLines().mkString("\n")).get
    read[V1VulnerabilityList](text).vulnerabilities(dependencyOf(file))
  }

  // Fixture names encode coordinates, e.g. "org.scala-lang_scala-library_2.13.1.json"
  private def dependencyOf(file: File): Dependency =
    file.getName.replace(".json", "").split("_").toList match {
      case List(org, art, ver) => Dependency(org, art, ver, file)
      case _ => fail(s"invalid filename for [${file.getName}]")
    }

  test("classifies the scala-library advisory with its CVSS score and severity bucket") {
    val vulnerabilities = classify("org.scala-lang_scala-library_2.13.1.json")

    assertEquals(vulnerabilities.size, 1)
    val vulnerability = vulnerabilities.head
    assertEquals(vulnerability.id, "GHSA-8qv5-68g4-248j")
    assert(vulnerability.scores.nonEmpty, "should carry a parsed CVSS score")
    assert(
      vulnerability.scores.exists(_.score >= 9.0),
      s"expected a critical score but got ${vulnerability.scores}"
    )
    assert(
      vulnerability.scores.exists(s => Vulnerability.Score.scoreSeverity(s.vector) == "critical"),
      s"expected the 'critical' bucket but got ${vulnerability.scores
          .map(s => Vulnerability.Score.scoreSeverity(s.vector))}"
    )
  }

  test("every committed fixture parses and the pipeline yields at least one scored advisory") {
    val folder = new File("src/test/resources/queries")
    val all    = folder
      .listFiles()
      .toSeq
      .filter(_.getName.endsWith(".json"))
      .flatMap { file =>
        val text = Using(Source.fromFile(file))(_.getLines().mkString("\n")).get
        read[V1VulnerabilityList](text).vulnerabilities(dependencyOf(file))
      }

    assert(all.nonEmpty, "fixtures should classify into vulnerabilities")
    assert(all.forall(_.id.nonEmpty), "every classified vulnerability must have an id")
    assert(
      all.exists(_.scores.exists(_.score > 0.0)),
      "at least one advisory should carry a parsed CVSS score"
    )
  }
}
