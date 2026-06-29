/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.settings

import java.io.File

import net.nmoncho.sbt.osv.OsvTestCompat
import net.nmoncho.sbt.osv.settings.SuppressionSettings.PackagedFilter
import sbt._
import sbt.internal.util.Attributed
import sbt.librarymanagement.ModuleID

class PackagedFilterSpec extends munit.FunSuite {

  // -------------------------------------------------------------------------
  // BlacklistAll/WhitelistAll
  // -------------------------------------------------------------------------

  test("BlacklistAll/WhitelistAll – rejects/accept any dependency") {
    val dep = Attributed.blank(new File("any.jar"))

    assert(!PackagedFilter.BlacklistAll(dep), "BlacklistAll should reject all dependencies")
    assert(PackagedFilter.WhitelistAll(dep), "WhitelistAll should accept all dependencies")
  }

  // -------------------------------------------------------------------------
  // ofFile
  // -------------------------------------------------------------------------

  test("ofFile – accepts/rejects when predicate returns true") {
    val accepted = Attributed.blank(new File("match.jar"))
    val rejected = Attributed.blank(new File("other.jar"))
    val filter   = PackagedFilter.ofFile(_.getName == "match.jar")

    assert(filter(accepted), "accept only certain files")
    assert(!filter(rejected), "reject files that aren't accepted")
  }

  // -------------------------------------------------------------------------
  // ofFilename
  // -------------------------------------------------------------------------

  test("ofFilename – accepts/rejects when filename matches") {
    val accepted = Attributed.blank(new File("/path/to/lib.jar"))
    val rejected = Attributed.blank(new File("/path/to/other.jar"))
    val filter   = PackagedFilter.ofFilename(_ == "lib.jar")

    assert(filter(accepted), "accept only based on filename")
    assert(!filter(rejected), "reject based on filename")
  }

  // -------------------------------------------------------------------------
  // ofFilenameRegex
  // -------------------------------------------------------------------------

  test("ofFilenameRegex – accepts/rejects filename that matches regex") {
    val accepted = Attributed.blank(new File("my-library-1.2.3.jar"))
    val filter   = PackagedFilter.ofFilenameRegex("my-library-.*\\.jar".r)
    val rejected = Attributed.blank(new File("other-library-1.0.jar"))

    assert(filter(accepted), "accept matching regex")
    assert(!filter(rejected), "reject non-matching regex")
  }

  // -------------------------------------------------------------------------
  // ofGav
  // -------------------------------------------------------------------------

  test("ofGav – accepts/reject dependency whose GAV satisfies the predicate") {
    val file     = new File("my-lib-1.0.0.jar")
    val accepted = OsvTestCompat.makeAttributed("org.example" % "my-lib" % "1.0.0", file)
    val rejected = OsvTestCompat.makeAttributed("org.other" % "my-lib" % "1.0.0", file)

    val filter = PackagedFilter.ofGav((org, _, _) => org == "org.example")

    assert(filter(accepted), "accept matching GAV")
    assert(!filter(rejected), "reject non-matching GAV")
  }

  test("ofGav – rejects dependency with no module metadata") {
    val dep    = Attributed.blank(new File("unknown.jar"))
    val filter = PackagedFilter.ofGav((_, _, _) => true)

    assert(!filter(dep))
  }

  test("ofGav – matches exact version") {
    val module = ModuleID("org.example", "lib", "3.0.0")
    val dep    = OsvTestCompat.makeAttributed(module, new File("lib-3.0.0.jar"))

    val matchVer = PackagedFilter.ofGav((_, _, ver) => ver == "3.0.0")
    val wrongVer = PackagedFilter.ofGav((_, _, ver) => ver == "2.0.0")

    assert(matchVer(dep))
    assert(!wrongVer(dep))
  }
}
