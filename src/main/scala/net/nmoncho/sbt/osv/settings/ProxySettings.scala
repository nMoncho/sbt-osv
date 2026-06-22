/*
 * Copyright 2025 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.settings

/** Proxy Settings
  *
  * @param disableSchemas Whether or not if using basic auth with a proxy the system setting
  *                       'jdk.http.auth.tunneling.disabledSchemes' should be set to an empty
  *                       string.
  * @param nonProxyHosts The properties key for the non proxy hosts.
  */
case class ProxySettings(
    @deprecated("No longer used; will be removed in a future release")
    disableSchemas: Option[Boolean],
    nonProxyHosts: Option[Seq[String]]
)

object ProxySettings {
  val Default: ProxySettings = new ProxySettings(None, None)

  def apply(
      disableSchemas: Option[Boolean]    = None,
      nonProxyHosts: Option[Seq[String]] = None
  ): ProxySettings =
    new ProxySettings(disableSchemas, nonProxyHosts)
}
