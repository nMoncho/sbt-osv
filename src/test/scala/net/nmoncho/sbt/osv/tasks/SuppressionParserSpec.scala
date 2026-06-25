/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package tasks

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import munit.FunSuite

class SuppressionParserSpec extends FunSuite {

  // -------------------------------------------------------------------------
  // splitBlocks
  // -------------------------------------------------------------------------

  test("splitBlocks – single block with no blank lines") {
    val input = lines(
      """# comment
        |CVE-1234-5678"""
    )

    assertEquals(SuppressionParser.splitBlocks(input).size, 1)
  }

  test("splitBlocks – two blocks separated by one blank line") {
    val input = lines(
      """CVE-0001-0001
        |
        |CVE-0002-0002"""
    )

    assertEquals(SuppressionParser.splitBlocks(input).size, 2)
  }

  test("splitBlocks – two blocks separated by multiple blank lines") {
    val input = lines(
      """CVE-0001-0001
        |
        |
        |
        |CVE-0002-0002"""
    )
    assertEquals(SuppressionParser.splitBlocks(input).size, 2)
  }

  test("splitBlocks – two blocks not separated by blank lines") {
    val input = lines(
      """CVE-0001-0001
        |CVE-0002-0002"""
    )
    assertEquals(SuppressionParser.splitBlocks(input).size, 2)
  }

  test("splitBlocks – leading and trailing blank lines are ignored") {
    val input = lines(
      """
        |CVE-0001-0001
        |
        |"""
    )

    val blocks = SuppressionParser.splitBlocks(input)

    assertEquals(blocks.size, 1)
    assertEquals(blocks.head, Seq("CVE-0001-0001"))
  }

  test("splitBlocks – empty input yields no blocks") {
    assertEquals(SuppressionParser.splitBlocks(Iterator.empty).size, 0)
  }

  test("splitBlocks – only blank lines yields no blocks") {
    val input = Seq("", "   ", "\t").iterator

    assertEquals(SuppressionParser.splitBlocks(input).size, 0)
  }

  // -------------------------------------------------------------------------
  // parseBlock – happy path
  // -------------------------------------------------------------------------

  test("parseBlock – name only") {
    val result = SuppressionParser.parseBlock(Seq("CVE-1234-5678"))

    assertRight(result, SuppressionRule(name = "CVE-1234-5678", comments = "", source = ""))
  }

  test("parseBlock – canonical order: comments then source then name") {
    val block = lines(
      """# This is a comment line
        |# This is the following comment line
        |# @source: foobar.jar
        |CVE-1234-556c"""
    ).toSeq

    assertRight(
      SuppressionParser.parseBlock(block),
      SuppressionRule(
        name     = "CVE-1234-556c",
        comments = "This is a comment line\nThis is the following comment line",
        source   = "foobar.jar"
      )
    )
  }

  test("parseBlock – source before comments") {
    val block = lines(
      """# @source: foobar.jar
        |# First comment
        |# Second comment
        |CVE-1234-556c"""
    ).toSeq

    assertRight(
      SuppressionParser.parseBlock(block),
      SuppressionRule(
        name     = "CVE-1234-556c",
        comments = "First comment\nSecond comment",
        source   = "foobar.jar"
      )
    )
  }

  test("parseBlock – comments interleaved with source") {
    val block = lines(
      """# First comment
        |# @source: foobar.jar
        |# Second comment
        |CVE-1234-556c"""
    ).toSeq

    assertRight(
      SuppressionParser.parseBlock(block),
      SuppressionRule(
        name     = "CVE-1234-556c",
        comments = "First comment\nSecond comment",
        source   = "foobar.jar"
      )
    )
  }

  test("parseBlock – source without comments") {
    val block = lines(
      """# @source: only-source.jar
        |CVE-2024-0001"""
    ).toSeq

    assertRight(
      SuppressionParser.parseBlock(block),
      SuppressionRule(name = "CVE-2024-0001", comments = "", source = "only-source.jar")
    )
  }

  test("parseBlock – single comment without source") {
    val block = lines(
      """# Just a note
        |CVE-2024-0002"""
    ).toSeq

    assertRight(
      SuppressionParser.parseBlock(block),
      SuppressionRule(name = "CVE-2024-0002", comments = "Just a note", source = "")
    )
  }

  test("parseBlock – trims surrounding whitespace from name and source") {
    val block = Seq("  # @source:   spaced.jar  ", "  CVE-0000-0000  ")

    assertRight(
      SuppressionParser.parseBlock(block),
      SuppressionRule(name = "CVE-0000-0000", comments = "", source = "spaced.jar")
    )
  }

  test("parseBlock – blank lines inside block are ignored") {
    val block = Seq("# comment", "", "  ", "CVE-1111-1111")

    assertRight(
      SuppressionParser.parseBlock(block),
      SuppressionRule(name = "CVE-1111-1111", comments = "comment", source = "")
    )
  }

  // -------------------------------------------------------------------------
  // parseBlock – error cases
  // -------------------------------------------------------------------------

  test("parseBlock – no name returns Left") {
    val block = Seq("# only a comment", "# @source: x.jar")
    assertLeft(SuppressionParser.parseBlock(block))
  }

  test("parseBlock – empty block returns Left") {
    assertLeft(SuppressionParser.parseBlock(Seq.empty))
  }

  test("parseBlock – multiple names returns Left") {
    val block = Seq("CVE-0001-0001", "CVE-0002-0002")
    assertLeft(SuppressionParser.parseBlock(block))
  }

  test("parseBlock – multiple @source entries returns Left") {
    val block = lines(
      """# @source: first.jar
        |# @source: second.jar
        |CVE-1234-5678"""
    ).toSeq

    assertLeft(SuppressionParser.parseBlock(block))
  }

  // -------------------------------------------------------------------------
  // parseLines
  // -------------------------------------------------------------------------

  test("parseLines – parses multiple valid rules") {
    val input = lines(
      """# Comment A
        |# @source: a.jar
        |CVE-0001-0001
        |
        |# Comment B
        |CVE-0002-0002"""
    )

    val (rules, errors) = SuppressionParser.parseLines(input)
    assertEquals(errors, Seq.empty[String])
    assertEquals(rules.size, 2)
    assertEquals(rules(0), SuppressionRule("CVE-0001-0001", "Comment A", "a.jar"))
    assertEquals(rules(1), SuppressionRule("CVE-0002-0002", "Comment B", ""))
  }

  test("parseLines – collects errors without aborting valid rules") {
    val input = lines(
      """# @source: a.jar
        |CVE-0001-0001
        |
        |# This block has no name – error
        |# @source: orphan.jar
        |
        |# valid again
        |CVE-0003-0003
        |CVE-2145-4566"""
    )
    val (rules, errors) = SuppressionParser.parseLines(input)
    assertEquals(rules.size, 3)
    assertEquals(errors.size, 1)
  }

  test("parseLines – empty input yields no rules and no errors") {
    val (rules, errors) = SuppressionParser.parseLines(Iterator.empty)
    assertEquals(rules, Seq.empty[SuppressionRule])
    assertEquals(errors, Seq.empty[String])
  }

  test("parseLines – only blank lines yields no rules and no errors") {
    val (rules, errors) = SuppressionParser.parseLines(Seq("", "  ", "").iterator)
    assertEquals(rules, Seq.empty[SuppressionRule])
    assertEquals(errors, Seq.empty[String])
  }

  // -------------------------------------------------------------------------
  // parseFile
  // -------------------------------------------------------------------------

  test("parseFile – reads and parses a temp file correctly") {
    val content =
      """# File comment
        |# @source: file.jar
        |CVE-9999-0001
        |""".stripMargin

    val tmpFile: Path = Files.createTempFile("suppression-test-", ".txt")
    try {
      Files.write(tmpFile, content.getBytes(StandardCharsets.UTF_8))

      val rules = SuppressionParser.parse(tmpFile.toFile)
      assertEquals(
        rules,
        Set(SuppressionRule("CVE-9999-0001", "File comment", "file.jar"))
      )
    } finally {
      Files.deleteIfExists(tmpFile)
    }
  }

  test("write/parse – reads and parses a temp file correctly") {
    val tmpFile: Path = Files.createTempFile("suppression-test-", ".txt")
    try {

      val expected = Set(
        SuppressionRule("CVE-9999-0001", "File comment", "file.jar"),
        SuppressionRule("CVE-1234-2345", "", "")
      )

      SuppressionParser.write(tmpFile.toFile, expected)
      val rules = SuppressionParser.parse(tmpFile.toFile)

      assertEquals(rules, expected)
    } finally {
      Files.deleteIfExists(tmpFile)
    }
  }

  test("parseFile – throws for a non-existent file") {
    intercept[java.io.FileNotFoundException] {
      SuppressionParser.parse(new java.io.File("/no/such/file/suppression.txt"))
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private def lines(raw: String): Iterator[String] = raw.stripMargin.linesIterator

  private def assertRight(
      result: Either[String, SuppressionRule],
      expected: SuppressionRule
  ): Unit =
    assertEquals(result, Right(expected))

  private def assertLeft(result: Either[String, SuppressionRule]): Unit =
    assert(result.isLeft, s"Expected Left but got: $result")

}
