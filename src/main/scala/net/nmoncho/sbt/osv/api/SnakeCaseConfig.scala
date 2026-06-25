/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import upickle.AttributeTagged

object SnakeCaseConfig extends AttributeTagged {

  // Utility: camelCase to snake_case (used for writing JSON)
  private def camelToSnake(s: String): String =
    s.replaceAll("([A-Z]+)", "_$1").toLowerCase

  // Utility: snake_case to camelCase (used for reading JSON)
  private def snakeToCamel(s: String): String = {
    val parts = s.split("_")
    if (parts.isEmpty) ""
    else parts.head + parts.tail.map(_.capitalize).mkString
  }

  // Maps JSON dictionary keys back to Scala case class fields
  override def objectAttributeKeyReadMap(s: CharSequence): CharSequence =
    snakeToCamel(s.toString)

  // Maps Scala case class fields to JSON dictionary keys
  override def objectAttributeKeyWriteMap(s: CharSequence): CharSequence =
    camelToSnake(s.toString)
}
