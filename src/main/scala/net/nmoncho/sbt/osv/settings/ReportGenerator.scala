package net.nmoncho.sbt.osv.settings

object ReportGenerator {

  sealed trait Format {
    def name(): String
  }

}
