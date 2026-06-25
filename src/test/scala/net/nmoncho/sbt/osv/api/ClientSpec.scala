/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.api

import net.nmoncho.sbt.osv.TestUtils
import net.nmoncho.sbt.osv.api.v1.V1BatchQuery
import net.nmoncho.sbt.osv.api.v1.V1Query
import sbt._

class ClientSpec extends munit.FunSuite with TestUtils {
  import TestUtils.moduleIdToDependency

  implicit val log: Logger = Logger.Null

  test("query vulnerabilities for a single package") {
    val client = new v1.Client()

    val result = client.query(
      V1Query.of("org.scala-sbt" % "sbt" % "1.11.7")
    )

    result match {
      case Right(value) =>
        assert(value.vulns.exists(_.size == 1), "The package has 1 vulnerability")
        assert(value.vulns.exists(_.head.id == "GHSA-x4ff-q6h8-v7gw"))

      case Left(value) =>
        fail(s"Couldn't parse or fetch vulnerabilities from the API: ${value.toString}")
    }
  }

  test("query vulnerabilities for a multiple packages") {
    val client = new v1.Client()

    val query = V1BatchQuery.of(
      "com.github.t3hnar"         % "scala-bcrypt_2.10"    % "2.6",
      "com.google.code.findbugs"  % "jsr305"               % "1.3.9",
      "com.google.http-client"    % "google-http-client"   % "1.22.0",
      "com.google.oauth-client"   % "google-oauth-client"  % "1.22.0",
      "commons-beanutils"         % "commons-beanutils"    % "1.9.1",
      "commons-codec"             % "commons-codec"        % "1.3",
      "commons-collections"       % "commons-collections"  % "3.2.1",
      "commons-logging"           % "commons-logging"      % "1.1.1",
      "de.svenkubiak"             % "jBCrypt"              % "0.4.1",
      "org.apache.commons"        % "commons-collections4" % "4.1",
      "org.apache.httpcomponents" % "httpclient"           % "4.0.1",
      "org.apache.httpcomponents" % "httpcore"             % "4.0.1",
      "org.scala-lang"            % "scala-library"        % "2.13.16"
    )
    val result = client.queryBatch(query)

    result match {
      case Right(value) =>
        assert(value.results.nonEmpty, "there should be a result for the query")
        val result = value.results.head

        assert(result.size == query.queries.size, "result must have the same amount as queries")
        assert(result.head.vulns == None, "'scala-bcrypt_2.10@2.6' has no vulnerabilities")

        val googleOAuthClient = result(3)
        assert(
          googleOAuthClient.vulns.nonEmpty,
          "'google-oauth-client@1.22.0' has some vulnerabilities"
        )

      case Left(value) =>
        fail(s"Couldn't parse or fetch vulnerabilities from the API: ${value.toString}")
    }
  }
}
