/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

/** Orders Maven version strings, e.g. "2.9.0" < "2.10.0" and "1.0-RC1" < "1.0".
  *
  * Versions are split on '.', '-' and '_'. Numeric segments compare as numbers,
  * qualifiers compare case-insensitively, and a missing segment counts as "0".
  * A qualifier ranks below any number, so pre-releases precede their release.
  */
private[osv] object MavenVersion {

  private val Separators = "[.\\-_]"
  private val Release    = "0"

  val ordering: Ordering[String] = (x: String, y: String) => {
    val xs = x.split(Separators)
    val ys = y.split(Separators)

    // First differing segment decides, e.g. "2.10.0" vs "2.9.0" -> 10 vs 9
    xs.zipAll(ys, Release, Release)
      .iterator
      .map { case (a, b) => segment(a, b) }
      .find(_ != 0)
      .getOrElse(0)
  }

  private def segment(a: String, b: String): Int =
    (number(a), number(b)) match {
      case (Some(x), Some(y)) => x.compare(y)
      case (Some(_), None) => 1
      case (None, Some(_)) => -1
      case (None, None) => a.compareToIgnoreCase(b)
    }

  private def number(s: String): Option[BigInt] =
    if (s.nonEmpty && s.forall(_.isDigit)) {
      Some(BigInt(s))
    } else {
      None
    }
}
