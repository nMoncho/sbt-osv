/*
 * Copyright 2026 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.sbt.osv.storage

import java.io.File
import java.sql.Connection

/** SQL Connection Provider contract */
trait ConnectionProvider {

  /** Creates a SQL connection to be used
    */
  def connection(): Connection

  /** Closes this Connection Provider when no more connections are needed
    */
  def close(): Unit

}

object ConnectionProvider {

  /** H2-in-memory connection provider
    *
    * Use in tests only!
    */
  def h2InMemory(): ConnectionProvider = new ConnectionProvider {
    private val driver = Class.forName("org.h2.Driver")

    private lazy val pool = org.h2.jdbcx.JdbcConnectionPool.create(
      "jdbc:h2:mem:",
      "sa",
      ""
    )

    override def connection(): Connection = pool.getConnection

    override def close(): Unit = pool.dispose()
  }

  /** H2-in-file connection provider
    *
    * The DB starts with PostgreSQL mode
    *
    * @param file where should the database file be located (found or created)
    */
  def h2InFile(file: File): ConnectionProvider = new ConnectionProvider {
    private val driver = Class.forName("org.h2.Driver")

    private lazy val pool = org.h2.jdbcx.JdbcConnectionPool.create(
      s"jdbc:h2:file:${file};FILE_LOCK=FILE;AUTO_SERVER=TRUE;LOCK_TIMEOUT=60000;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE SCHEMA IF NOT EXISTS public",
      "sa",
      ""
    )

    override def connection(): Connection = pool.getConnection

    override def close(): Unit = pool.dispose()
  }

  /** Creates the OSV schema for a give connection
    */
  def createSchema(conn: Connection): Unit =
    scala.util
      .Using(
        scala.io.Source
          .fromInputStream(getClass.getClassLoader.getResourceAsStream("db/schema.sql"))
      ) { stream =>
        val schema = stream.mkString

        val stmt = conn.createStatement()
        schema.split(";").map(_.trim).filter(_.nonEmpty).foreach(stmt.execute)
        stmt.close()
      }
      .get

}
