package com.pacbio.database

import java.sql.Connection

import org.specs2.mutable.Specification


/**
 * Covers core database connection tests
 *
 * This work is part of the PR that introduced connection pooling and a work-around for using SQLite
 * with Flyway migrations.
 *
 * @see https://github.com/PacificBiosciences/smrtflow/pull/126
 *
 * Prior to this work, the codebase would experience database locking (aka "SQLITE_BUSY") style
 * errors. The root of the problem was that Flyway required two Connection instances to the database
 * but SQLite only allowed one. The two connections are to lock the db's schema and to perform
 * migrations. The work-around is to have a special case for migrations where the same Connection
 * object is cached and returned to Flyway. Other JDBC use in the codebase works with normal DBCP
 * pooling.
 *
 * This spec also exercises a guard against forgetting to close Connection objects or attempting to
 * open multiple Connections simultaneously. If we had such a guard, it'd have made the root of the
 * underlying SQLite locking issues more obvious.
 *
 * All around, this Spec should ensure that SQLite continues being used correctly by our codebase.
 * If we ever switch databases (say to Postgres), then this Spec and the custom JDBC Datasource
 * can be scrapped in favor of standard JDBC driver use and connection pooling via DBCP.
 * @author Jayson Falkner - jfalkner@pacificbiosciences.com
 */
class SqliteAndFlywayUsageSpec extends Specification {

  // force these tests to run sequentially since they can lock up the database
  sequential

  val db = new TestDatabase()

  "Connection pooling for SQLite" should {
    "be in use" in {
      db.dbUri startsWith "jdbc:sqlite:" must beTrue
    }
    "share Connection instances during Flyway migrations" in {
      db.setShareConnection(true)
      val (conn1, conn2) = (db.connection, db.connection)
      try conn1 mustEqual conn2
      finally List.apply[Connection](conn1, conn2).foreach(x => x.close())
    }
    "guard against failing to close Connection instances" in {
      db.setShareConnection(false)
      val conn1 = db.connection
      try db.connection must throwA[RuntimeException]
      finally conn1.close()
    }
    "return unique Connection instances during normal, non-migration use" in {
      db.setShareConnection(false)
      val conn1 = db.connection
      conn1.close()
      val conn2 = db.connection
      conn2.close()
      conn1 mustNotEqual conn2
    }
  }
}

