/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.io.File

import net.nmoncho.sbt.osv.api.v1.Client
import net.nmoncho.sbt.osv.api.v1.V1BatchQuery
import net.nmoncho.sbt.osv.api.v1.V1BatchVulnerabilityList
import net.nmoncho.sbt.osv.api.v1.V1Query
import net.nmoncho.sbt.osv.settings.EngineSettings
import net.nmoncho.sbt.osv.storage.ConnectionProvider
import net.nmoncho.sbt.osv.storage.VulnerabilityRepository
import sbt.Logger
import sbt.io.IO

/** Dependency Scanner */
trait Engine {

  /** Analyze dependencies for vulnerabilities
    *
    * @param failCvssScore fail CVSS score (inclusive)
    * @param dependencies dependencies to scan
    * @param suppressions suppressions to include
    * @return scan result
    */
  def analyzeDependencies(
      failCvssScore: Double,
      dependencies: Set[Dependency],
      suppressions: Set[SuppressionRule]
  )(implicit log: Logger): Engine.ScanResult

  /** Cleans up the scanner after use */
  def close(): Unit

  /** Writes a report to a given file
    *
    * @param projectName project name this report is for
    * @param outputDir on what folder to write the report to
    * @param report report as a string
    */
  def writeReports(projectName: String, outputDir: sbt.File, report: String): Unit

}

object Engine {

  final val DefaultOsvDB = "osv.db"

  def create(settings: EngineSettings): Engine = {
    val dbFile = settings.dataDirectory match {
      case Some(parent) if parent.exists() && parent.isDirectory =>
        new File(parent, DefaultOsvDB)

      case Some(value) => value

      case None =>
        EngineSettings.findDataDirectory()
    }

    new Default(
      settings,
      ConnectionProvider.h2InFile(dbFile)
    )
  }

  /** Vulnerability scan result
    *
    * @param vulnerabilities detected vulnerabilities by its dependency
    * @param suppressed suppressed vulnerabilities
    * @param unusedSuppressions unused suppressions
    */
  case class ScanResult(
      vulnerabilities: Map[Dependency, Set[Vulnerability]],
      suppressed: Set[Vulnerability],
      unusedSuppressions: Set[SuppressionRule]
  )

  /** Default implementation for [[Engine]]
    *
    * @param settings engine settings
    * @param db database connection provider to use for caching
    */
  class Default(settings: EngineSettings, db: ConnectionProvider) extends Engine {
    private val client = Client(settings.baseUrl)

    private lazy val repository = {
      val connection = db.connection()
      ConnectionProvider.createSchema(connection)

      VulnerabilityRepository.jdbc(connection)
    }

    override def analyzeDependencies(
        failCvssScore: Double,
        dependencies: Set[Dependency],
        suppressions: Set[SuppressionRule]
    )(implicit log: Logger): ScanResult = {
      val ((queries, toQuery), vulnerabilitiesInDB) = dependencies.foldLeft(
        Vector.empty[V1Query] -> Vector.empty[Dependency] -> Map
          .empty[Dependency, Set[Vulnerability]]
      ) { case ((toProcess @ (qs, deps), vulns), d) =>

        val q = V1Query.of(d)

        repository.findCached(q, settings.cacheEviction) match {
          case Some(inDB) =>
            val vs = inDB.flatMap(_.toVulnerability()).toSet
            toProcess -> (vulns + (d -> vs))

          case None =>
            (qs :+ q, deps :+ d) -> vulns
        }
      }

      val vulnerabilitiesInAPI = client.queryBatch(V1BatchQuery(queries)) match {
        case Right(V1BatchVulnerabilityList(Some(result))) =>
          handleBatchResults(toQuery.zip(result))

        case Right(V1BatchVulnerabilityList(None)) =>
          Map.empty[Dependency, Set[Vulnerability]]

        case Left(value) =>
          throw new IllegalStateException(s"Failed to query OSV API. Cause: ${value.toString}")
      }

      processDependencies(
        vulnerabilitiesInDB ++ vulnerabilitiesInAPI,
        suppressions
      )
    }

    private def processDependencies(
        vulnerabilities: Map[Dependency, Set[Vulnerability]],
        suppressions: Set[SuppressionRule]
    ): ScanResult = {
      val suppressionsByName = suppressions.view.map(s => s.name -> s).toMap

      // Process all dependencies and vulnerabilities, accumulate a:
      //    - Map of dependencies with unsuppressed vulnerabilities
      //    - Set of suppressed vulnerabilities
      //    - Set of _used_ suppressions
      val (processed, suppressed, used) = vulnerabilities.foldLeft(
        (
          Map.empty[Dependency, Set[Vulnerability]],
          Set.empty[Vulnerability],
          Set.empty[SuppressionRule]
        )
      ) { case ((processedSoFar, suppressedSoFar, usedSoFar), (dependency, vulnerabilities)) =>
        val (nonSuppressed, suppressed, used) =
          vulnerabilities.foldLeft((Set.empty[Vulnerability], suppressedSoFar, usedSoFar)) {
            case ((currentVulnerabilities, currentSuppressions, usedSuppressions), vulnerability) =>
              // If the vulnerability has to be suppressed:
              //    - add it to the used suppression,
              //    - and ignore it from the vulnerabilities
              suppressionsByName.get(vulnerability.id) match {
                case Some(suppression) =>
                  (
                    currentVulnerabilities,
                    currentSuppressions + vulnerability,
                    usedSuppressions + suppression
                  )

                case None =>
                  (currentVulnerabilities + vulnerability, currentSuppressions, usedSuppressions)
              }
          }

        (
          processedSoFar + (dependency -> nonSuppressed),
          suppressed,
          used
        )
      }

      ScanResult(
        vulnerabilities = processed,
        suppressed      = suppressed,
        // get unused suppressions by making the difference between used and all
        unusedSuppressions = suppressions -- used
      )
    }

    private def handleBatchResults(
        toProcess: Vector[(Dependency, V1BatchVulnerabilityList.Value)]
    )(implicit log: Logger): Map[Dependency, Set[Vulnerability]] = {
      import V1BatchVulnerabilityList.Value

      toProcess.map {
        // dependency has some vulnerabilities
        // for now we ignore the result and query all vulnerabilities
        // maybe we can optimize this
        case (dep, Value(Some(_), _)) =>
          val query = V1Query.of(dep)

          client.query(query) match {
            case Right(value) =>
              repository.cache(query, value.vulns.getOrElse(Seq.empty))
              dep -> value.vulnerabilities()

            case Left(value) =>
              throw new IllegalStateException(s"Failed to query OSV API. Cause: ${value.toString}")
          }

        // dependency has no found vulnerabilities
        case (dep, Value(None, _)) =>
          dep -> Set.empty[Vulnerability]
      }.toMap
    }

    override def close(): Unit =
      db.close()

    override def writeReports(projectName: String, outputDir: sbt.File, report: String): Unit =
      IO.write(new File(outputDir, projectName), report)
  }
}
