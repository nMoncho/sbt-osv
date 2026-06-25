/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv
package api

import java.time.Instant

import requests.Response
import sbt.Logger

package object v1 {

  import SnakeCaseConfig.*

  implicit val instantReadWriter: ReadWriter[Instant] =
    readwriter[String].bimap(_.toString, Instant.parse)

  // TODO handle pagination

  class Client private (baseUrl: String) {

    /** Queries the vulnerabilities for a given package
      *
      * See also <a href="https://google.github.io/osv.dev/post-v1-query/">docs</a>
      *
      * @param q query to run against API
      * @return either the vulnerability list, which can be empty, or a failed result
      */
    def query(q: V1Query)(implicit log: Logger): Either[RpcStatus, V1VulnerabilityList] =
      handleResponse[V1VulnerabilityList](
        requests.post(
          url   = s"${baseUrl}/v1/query",
          data  = write(q),
          check = false
        )
      )

    /** Queries the vulnerabilities for several packages
      *
      * See also <a href="https://google.github.io/osv.dev/post-v1-querybatch/">docs</a>
      *
      * @param q batch query to run against API
      * @return either the vulnerability list, which can be empty, or a failed result
      */
    def queryBatch(
        q: V1BatchQuery
    )(implicit log: Logger): Either[RpcStatus, V1BatchVulnerabilityList] =
      handleResponse[V1BatchVulnerabilityList](
        requests.post(
          url   = s"${baseUrl}/v1/querybatch",
          data  = write(q),
          check = false
        )
      )

    /** Queries vulnerability information for a given ID
      *
      * See also <a href="https://google.github.io/osv.dev/get-v1-vulns/">docs</a>
      *
      * @param id vulnerability ID
      * @return either the vulnerability, or a failed result
      */
    def vulnerability(id: String)(implicit log: Logger): Either[RpcStatus, OsvVulnerability] =
      handleResponse[OsvVulnerability](
        requests.get(
          url   = s"${baseUrl}/v1/vulns/${id}",
          check = false
        )
      )

    private def handleResponse[A: Reader](
        response: Response
    )(implicit log: Logger): Either[RpcStatus, A] =
      try {
        if (response.is2xx) {
          Right(read[A](response.text()))
        } else {
          Left(read[RpcStatus](response.text()))
        }
      } catch {
        case t: Throwable =>
          logThrowable(t)
          Left(
            RpcStatus(
              Some(response.statusCode),
              Some(s"Something went wrong. Cause: ${t.getMessage}")
            )
          )
      }
  }

  object Client {
    def apply(baseUrl: String = "https://api.osv.dev"): Client =
      new Client(if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl)
  }
}
