/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv

import java.io.File
import java.sql.Connection
import java.util.concurrent.Executors

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

import net.nmoncho.sbt.osv.api.OsvVulnerability
import net.nmoncho.sbt.osv.api.RpcStatus
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

  /** Purges any cache this implementation may be using
    */
  def purgeCache()(implicit log: Logger): Unit = ()
}

object Engine {

  final val DefaultOsvDB = "osv.db"

  /** OSV enforces a hard maximum of 1000 queries per `querybatch` request, so larger
    * dependency sets (cold cache, big aggregate builds) must be split into chunks.
    */
  final val MaxBatchQueries = 1000

  def create(settings: EngineSettings): Engine = {
    val dbFile = settings.dataDirectory match {
      case Some(parent) if parent.exists() && parent.isDirectory =>
        new File(parent, DefaultOsvDB)

      case Some(value) => value

      case None =>
        EngineSettings.findDataDirectory()
    }

    new Default(
      settings = settings,
      client   = Client(
        settings.baseUrl,
        connectTimeout = settings.connectionTimeout,
        readTimeout    = settings.connectionReadTimeout
      ),
      db                 = ConnectionProvider.h2InFile(dbFile),
      repositoryProvider = connection => {
        ConnectionProvider.createSchema(connection)
        VulnerabilityRepository.jdbc(connection)
      }
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
  class Default(
      settings: EngineSettings,
      client: Client,
      db: ConnectionProvider,
      repositoryProvider: Connection => VulnerabilityRepository
  ) extends Engine {

    // The cache database is entirely best-effort. Opening it, reading from it, and
    // writing to it can all fail (for example when a concurrent build contends for
    // the shared H2 file on CI). None of those failures must ever fail the scan: on
    // any database error we warn and carry on, falling back to the OSV API as if the
    // cache were empty, and skipping writes (the next run re-fetches and re-caches).
    private var repositoryResolved: Boolean                         = false
    private var resolvedRepository: Option[VulnerabilityRepository] = None

    /** Lazily opens the cache repository, memoizing the outcome. Returns `None` (cache
      * disabled for this run) if the database cannot be opened.
      */
    private def repository()(implicit log: Logger): Option[VulnerabilityRepository] = {
      if (!repositoryResolved) {
        repositoryResolved = true
        resolvedRepository =
          try Some(repositoryProvider(db.connection()))
          catch {
            case NonFatal(e) =>
              log.warn(
                s"Could not open the OSV cache database; the scan will proceed without a cache. Cause: ${e.getMessage}"
              )
              None
          }
      }

      resolvedRepository
    }

    /** Reads a cached result, treating any database failure as a cache miss so the
      * caller queries the OSV API instead.
      */
    private def findCached(query: V1Query)(implicit log: Logger): Option[Seq[OsvVulnerability]] =
      repository().flatMap { repo =>
        try repo.findCached(query, settings.cacheEviction)
        catch {
          case NonFatal(e) =>
            log.warn(
              s"Could not read the OSV cache; querying the OSV API instead. Cause: ${e.getMessage}"
            )
            None
        }
      }

    /** Writes a result to the cache, ignoring any database failure (the entry will be
      * re-fetched and re-cached on the next run).
      */
    private def cache(query: V1Query, vulnerabilities: Seq[OsvVulnerability])(
        implicit log: Logger
    ): Unit =
      repository().foreach { repo =>
        try repo.cache(query, vulnerabilities)
        catch {
          case NonFatal(e) =>
            log.warn(
              s"Could not write to the OSV cache; it will be re-fetched next run. Cause: ${e.getMessage}"
            )
        }
      }

    override def analyzeDependencies(
        failCvssScore: Double,
        dependencies: Set[Dependency],
        suppressions: Set[SuppressionRule]
    )(implicit log: Logger): ScanResult = withAnalysisTimeout {
      val ((queries, toQuery), vulnerabilitiesInDB) = dependencies.foldLeft(
        Vector.empty[V1Query] -> Vector.empty[Dependency] -> Map
          .empty[Dependency, Set[Vulnerability]]
      ) { case ((toProcess @ (qs, deps), vulns), d) =>

        val q = V1Query.of(d)

        findCached(q) match {
          case Some(inDB) =>
            val vs = inDB.map(_.toVulnerability()).toSet
            toProcess -> (vulns + (d -> vs))

          case None =>
            (qs :+ q, deps :+ d) -> vulns
        }
      }

      // Split the uncached queries into chunks no larger than OSV's per-request cap
      // and merge the per-chunk results back together in dependency order.
      val vulnerabilitiesInAPI: Map[Dependency, Set[Vulnerability]] =
        toQuery
          .zip(queries)
          .grouped(MaxBatchQueries)
          .foldLeft(Map.empty[Dependency, Set[Vulnerability]]) { (acc, chunk) =>
            val chunkDeps    = chunk.map(_._1)
            val chunkQueries = chunk.map(_._2)

            client.queryBatch(V1BatchQuery(chunkQueries)) match {
              case Right(V1BatchVulnerabilityList(Some(result))) =>
                acc ++ handleBatchResults(chunkDeps.zip(result))

              case Right(V1BatchVulnerabilityList(None)) =>
                acc

              case Left(value) =>
                apiUnavailable(value)
            }
          }

      processDependencies(
        excludeWithdrawn(vulnerabilitiesInDB ++ vulnerabilitiesInAPI),
        suppressions
      )
    }

    /** Bounds the analysis by `settings.analysisTimeout` when set. The work runs on a
      * dedicated thread and, if it overruns, that thread is interrupted and a clear
      * error is raised instead of letting the build hang indefinitely. When no timeout
      * is configured the work runs inline with no extra threading.
      */
    private def withAnalysisTimeout[A](body: => A): A =
      settings.analysisTimeout match {
        case Some(timeout) =>
          val executor                      = Executors.newSingleThreadExecutor()
          implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executor)

          try {
            Await.result(
              Future(body),
              Duration(timeout.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            )
          } catch {
            case ex: java.util.concurrent.TimeoutException =>
              throw new IllegalStateException(
                s"OSV analysis exceeded the configured timeout of [${timeout.toString}]",
                ex
              )
          } finally {
            executor.shutdownNow()
          }

        case None =>
          body
      }

    /** Removes withdrawn (retracted) advisories from the findings. A withdrawn OSV
      * entry is no longer valid, so keeping it would inflate the vulnerability count
      * and could fail the build on an advisory that no longer applies.
      */
    private def excludeWithdrawn(
        vulnerabilities: Map[Dependency, Set[Vulnerability]]
    )(implicit log: Logger): Map[Dependency, Set[Vulnerability]] =
      vulnerabilities.map { case (dependency, vulns) =>
        val (withdrawn, active) = vulns.partition(_.isWithdrawn)

        withdrawn.foreach { v =>
          log.debug(s"Ignoring withdrawn advisory [${v.id}] for [${dependency.coordinates}]")
        }

        dependency -> active
      }

    private def processDependencies(
        vulnerabilities: Map[Dependency, Set[Vulnerability]],
        suppressions: Set[SuppressionRule]
    ): ScanResult = {
      val suppressionsByName = suppressions.view.map(s => s.name -> s).toMap

      // A suppression rule matches a vulnerability by its primary OSV id OR any of
      // its aliases. OSV reports Maven-ecosystem advisories under a `GHSA-...` id
      // while carrying the CVE only as an alias (and vice-versa), so users writing
      // a rule against the CVE they know must still suppress the finding.
      def matchingSuppressions(vulnerability: Vulnerability): Set[SuppressionRule] =
        (vulnerability.aliases + vulnerability.id).flatMap(suppressionsByName.get)

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
              val matched = matchingSuppressions(vulnerability)

              if (matched.nonEmpty) {
                (
                  currentVulnerabilities,
                  currentSuppressions + vulnerability,
                  usedSuppressions ++ matched
                )
              } else {
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
              cache(query, value.vulns.getOrElse(Seq.empty))
              dep -> value.vulnerabilities()

            case Left(value) =>
              apiUnavailable(value)
          }

        // dependency has no found vulnerabilities
        case (dep, Value(None, _)) =>
          // prevent cache-miss if dep doesn't have vulnerabilities
          cache(V1Query.of(dep), Seq.empty)
          dep -> Set.empty[Vulnerability]
      }.toMap
    }

    /** Aborts the scan with a clear, actionable message when the OSV API could not be
      * reached (after retries). The scan fails closed: an unreachable data source must
      * not be silently reported as "no vulnerabilities".
      */
    private def apiUnavailable(status: RpcStatus): Nothing =
      throw new IllegalStateException(
        "Could not complete the OSV vulnerability scan because the OSV API was unavailable" +
          status.message.map(m => s": $m").getOrElse(".") +
          " Verify network connectivity and the OSV API status, then retry."
      )

    override def close(): Unit =
      // Best-effort: closing the cache must never fail the build either (e.g. when
      // the database could not be opened in the first place).
      try db.close()
      catch { case NonFatal(_) => () }

    override def writeReports(projectName: String, outputDir: sbt.File, report: String): Unit =
      IO.write(new File(outputDir, projectName), report)

    override def purgeCache()(implicit log: Logger): Unit = {
      log.info("Purging OSV cache")
      repository().foreach(_.purge())
    }
  }
}
