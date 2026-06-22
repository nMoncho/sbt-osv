package net.nmoncho.sbt.osv.tasks

import sbt.File

class SuppressionParser {

  def parse(f: File): Seq[String] = ???

  def write(file: File, rules: Set[String]): Unit = ???

}
