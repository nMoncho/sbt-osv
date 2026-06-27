/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt

import java.io.PrintWriter
import java.io.StringWriter

import scala.util.Using

import sbt.Logger

package object osv {

  /** Logs a [[Throwable]] with the corresponding stacktrace
    *
    * @param t throwable to log
    * @param log logger
    */
  def logThrowable(t: Throwable)(implicit log: Logger): Unit =
    // We have to log the full StackTraces here, since SBT doesn't use `printStackTrace`
    // when logging exceptions.
    Using.Manager { use =>
      val sw = use(new StringWriter)
      val pw = new PrintWriter(sw, true)

      t.printStackTrace(pw)
      log.error(sw.toString)
    }

}
