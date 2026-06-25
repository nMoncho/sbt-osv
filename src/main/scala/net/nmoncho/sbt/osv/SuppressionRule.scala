/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

/** Suppression rule
  *
  * @param name id, or alias, of vulnerability to suppress
  * @param comments any comments associated with this suppression
  * @param source where is the suppression coming from
  */
case class SuppressionRule(name: String, comments: String, source: String)
