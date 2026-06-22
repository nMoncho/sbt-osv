package net.nmoncho.sbt.osv

import sbt.File

case class Dependency(groupId: String, artifactId: String, revision: String, file: File)
