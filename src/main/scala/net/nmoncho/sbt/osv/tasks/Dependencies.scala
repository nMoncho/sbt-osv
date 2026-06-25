/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.tasks

import net.nmoncho.sbt.osv.Keys.osvScopes
import net.nmoncho.sbt.osv.Keys.osvSkip
import net.nmoncho.sbt.osv.OsvCompat
import sbt.Keys._
import sbt._
import sbt.internal.util.Attributed
import sbt.plugins.JvmPlugin
import xsbti.FileConverter

object Dependencies {

  lazy val projectDependencies: Def.Initialize[Task[Set[Attributed[File]]]] = Def.taskDyn {
    if (!thisProject.value.autoPlugins.contains(JvmPlugin) || (osvSkip ?? false).value) {
      Def.task(Set.empty)
    } else {
      Def.task {
        implicit val log: Logger              = streams.value.log
        implicit val converter: FileConverter = fileConverter.value

        val dependencies       = scala.collection.mutable.Set[Attributed[File]]()
        val scopes             = osvScopes.value
        val classpathTypeValue = classpathTypes.value
        val updateValue        = update.value

        if (scopes.compile) {
          dependencies ++= logAddDependencies(
            OsvCompat.managedJars(Compile, classpathTypeValue, updateValue, converter),
            Compile
          )
        }

        if (scopes.test) {
          dependencies ++= logAddDependencies(
            OsvCompat.managedJars(Test, classpathTypeValue, updateValue, converter),
            Test
          )
        }

        // Provided dependencies are include in Compile dependencies: remove instead of adding
        if (scopes.provided) {
          dependencies ++= logAddDependencies(
            OsvCompat.managedJars(Provided, classpathTypeValue, updateValue, converter),
            Provided
          )
        }

        if (scopes.runtime) {
          dependencies ++= logAddDependencies(
            OsvCompat.managedJars(Runtime, classpathTypeValue, updateValue, converter),
            Runtime
          )
        }

        // Optional dependencies are include in Compile dependencies: remove instead of adding
        if (scopes.optional) {
          dependencies ++= logAddDependencies(
            OsvCompat.managedJars(Optional, classpathTypeValue, updateValue, converter),
            Optional
          )
        }

        dependencies.toSet
      }
    }
  }

}
