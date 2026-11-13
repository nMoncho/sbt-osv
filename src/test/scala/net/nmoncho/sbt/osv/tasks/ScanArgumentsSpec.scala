/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package tasks

import munit.FunSuite
import sbt.complete.Parser

class ScanArgumentsSpec extends FunSuite {

  // Parses `osvScan` arguments as typed after the task name, e.g. " show-suppressed"
  private def parse(input: String): Either[String, Seq[ParseOptions]] =
    Parser.parse(input, Scan.argumentsParser)

  test("show-suppressed is parsed") {
    assertEquals(parse(" show-suppressed"), Right(Seq(ParseOptions.ShowSuppressed)))
  }

  test("show-suppressed combines with other arguments") {
    assertEquals(
      parse(" list-unused-suppressions show-suppressed"),
      Right(Seq(ParseOptions.ListUnusedSuppressions, ParseOptions.ShowSuppressed))
    )
  }

  test("show-suppressed is absent when not given") {
    assertEquals(parse(" list-unused-suppressions"), Right(Seq(ParseOptions.ListUnusedSuppressions)))
  }

  test("misspelled show-suppressed is rejected") {
    assert(parse(" show-suppresed").isLeft)
  }
}
