version := "0.1"
lazy val root = project.in(file("."))
scalaVersion := "2.13.16"

// Affected by GHSA-p66x-2cv9-qq3v, among others
libraryDependencies += "commons-beanutils" % "commons-beanutils" % "1.9.1"
