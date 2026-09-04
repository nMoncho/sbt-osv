import sbt.*

object Dependencies {
  lazy val cvssCalculator  = "us.springett"             % "cvss-calculator"   % "1.5.1"
  lazy val packageUrlJava  = "com.github.package-url"   % "packageurl-java"   % "1.5.0"
  lazy val requests        = "com.lihaoyi"             %% "requests"          % "0.9.3"
  lazy val uPickle         = "com.lihaoyi"             %% "upickle"           % "4.4.3"
  lazy val h2              = "com.h2database"           % "h2"                % "2.4.240"
  lazy val munit           = "org.scalameta"           %% "munit"             % "1.3.5"
  lazy val munitScalaCheck = "org.scalameta"           %% "munit-scalacheck"  % "1.3.1"
  lazy val log4jSf4jImpl   = "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.26.1"
  lazy val mockito         = "org.mockito"              % "mockito-core"      % "5.23.0"
}
