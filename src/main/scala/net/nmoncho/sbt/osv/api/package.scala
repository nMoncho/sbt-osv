package net.nmoncho.sbt.osv

import sbt.Logger

import java.time.Instant

package object api {

  import upickle.default._

  implicit val instantReadWriter: ReadWriter[Instant] =
    readwriter[String].bimap(_.toString, Instant.parse)

  class Client(baseUrl: String = "https://api.osv.dev") {
    def query(q: V1Query)(implicit log: Logger): Either[RpcStatus, V1VulnerabilityList] = {
      val response = requests.post(
        url   = s"${baseUrl}/v1/query",
        data  = write(q),
        check = false
      )

      try {
        if (response.is2xx) {
          Right(read[V1VulnerabilityList](response.text()))
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

    def queryBatch(q: V1BatchQuery): Either[RpcStatus, V1BatchVulnerabilityList] = ???

    def vulnerability(id: String): Either[RpcStatus, OsvVulnerability] = ???
  }
}
