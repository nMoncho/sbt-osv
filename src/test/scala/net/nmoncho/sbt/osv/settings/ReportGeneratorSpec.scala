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
      net.nmoncho.sbt.osv.html.html.report(model).toString()
    )
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

        d -> read[V1VulnerabilityList](jsonText).vulnerabilities()
      }
      .toMap
  }
}
