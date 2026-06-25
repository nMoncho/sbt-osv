/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package tasks

import scala.jdk.CollectionConverters._

import sbt.File

object SuppressionParser {

  private val SourcePrefix  = "# @source:"
  private val CommentPrefix = "#"

  /** Parses a suppression file
    *
    * @param file file to parse
    * @return parsed suppressions rules
    */
  def parse(file: File): Set[SuppressionRule] = {
    val (parsed, errors) = parseLines(sbt.IO.read(file).lines().iterator().asScala)

    if (errors.nonEmpty) {
      throw new IllegalArgumentException(
        s"""Failed to parse suppression file [${file.getAbsolutePath}] with the following errors:
           |${errors.mkString("- ", "\n- ", "")}
           |""".stripMargin
      )
    }

    parsed.toSet
  }

  /** Writes a suppression file with specified rules
    *
    * @param file suppression file
    * @param rules rules to include in the file
    */
  def write(file: File, rules: Set[SuppressionRule]): Unit = {
    val content = rules.toSeq.sortBy(_.name).flatMap { rule =>
      Seq(
        Option(rule.comments)
          .filterNot(_.trim.isBlank)
          .map(_.split("\n").mkString(s"$CommentPrefix ", s"\n$CommentPrefix ", "")),
        Option(rule.source)
          .filterNot(_.trim.isBlank)
          .map(_.replaceAll("\n", ""))
          .map(comments => s"${SourcePrefix}${comments}"),
        Option(rule.name)
          .filterNot(_.trim.isBlank)
      ).flatten
    }

    sbt.IO.writeLines(file, content)
  }

  /** Parse an entire file give its lines
    */
  private[tasks] def parseLines(lines: Iterator[String]): (Seq[SuppressionRule], Seq[String]) = {
    val results = splitBlocks(lines).map(parseBlock)

    val rules  = results.collect { case Right(r) => r }
    val errors = results.collect { case Left(e) => e }

    (rules, errors)
  }

  /** Split a sequence of file lines into logical blocks.
    *
    * A new block starts when:
    *  - a blank line is encountered (classic separator), or
    *  - any non-blank line follows a name line (implicit boundary), which
    *    allows two consecutive rule names with no blank line in between:
    *
    *    CVE-0001-0001          ← name  → end of block 1
    *    CVE-0002-0002          ← start of block 2
    *
    *    or a comment immediately after a name:
    *
    *    CVE-0001-0001          ← name  → end of block 1
    *    # comment for next     ← start of block 2
    *    CVE-0002-0002
    */
  private[tasks] def splitBlocks(lines: Iterator[String]): Seq[Seq[String]] = {
    case class State(blocks: Seq[Seq[String]], seenName: Boolean)

    lines
      .foldLeft(State(Seq(Seq.empty[String]), seenName = false)) {
        case (State(blocks, seenName), rawLine) =>
          val line = rawLine.trim
          if (line.isEmpty) {
            // blank line → close the current block (if non-empty) and open a fresh one
            val nextBlocks = if (blocks.last.isEmpty) blocks else blocks :+ Seq.empty[String]
            State(nextBlocks, seenName = false)
          } else if (seenName) {
            // implicit boundary: the previous block already has its name, so
            // start a new block and place this line into it
            State(blocks :+ Seq(line), seenName = isName(line))
          } else {
            // still accumulating into the current block
            State(blocks.init :+ (blocks.last :+ line), seenName = isName(line))
          }
      }
      .blocks
      .filter(_.nonEmpty)
  }

  private def isComment(line: String): Boolean = line.startsWith(CommentPrefix)
  private def isName(line: String): Boolean    = line.nonEmpty && !isComment(line)

  /** Parse a single block of lines into a SuppressionRule.
    *
    * A block looks like:
    *   # Optional comment line 1
    *   # Optional comment line 2
    *   # @source: foobar.jar
    *   CVE-1234-556c
    *
    * Rules:
    *  - Lines starting with `# @source:` are the source field.
    *  - Other lines starting with `#` are comment lines (joined with \n).
    *  - The first non-comment, non-blank line is the name.
    *  - `source` and `comments` may appear in any order before the name.
    */
  private[tasks] def parseBlock(lines: Seq[String]): Either[String, SuppressionRule] = {
    val trimmed = lines.map(_.trim).filter(_.nonEmpty)

    val sourceLines  = trimmed.filter(_.startsWith(SourcePrefix))
    val commentLines =
      trimmed.filter(l => l.startsWith(CommentPrefix) && !l.startsWith(SourcePrefix))
    val nameLines = trimmed.filterNot(_.startsWith(CommentPrefix))

    if (nameLines.isEmpty) {
      Left(s"No name found in block: ${lines.mkString("|")}")
    } else if (nameLines.size > 1) {
      Left(s"Multiple name candidates found in block: ${nameLines.mkString(", ")}")
    } else if (sourceLines.size > 1) {
      Left(s"Multiple @source entries found in block: ${sourceLines.mkString(", ")}")
    } else {
      val name   = nameLines.head
      val source = sourceLines.headOption
        .map(_.stripPrefix(SourcePrefix).trim)
        .getOrElse("")
      val comments = commentLines
        .map(_.stripPrefix(CommentPrefix).trim)
        .mkString("\n")

      Right(SuppressionRule(name = name, comments = comments, source = source))
    }

  }

}
