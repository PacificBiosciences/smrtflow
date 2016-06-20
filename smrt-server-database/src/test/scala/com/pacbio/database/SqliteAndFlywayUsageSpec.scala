package com.pacbio.database

import java.sql.Connection

import org.specs2.mutable.Specification


/**
 * Exposes state needed for checking database pooling
 *
 * Historically, this work was part of the PR that introduced connection pooling and a work-around
 * for using SQLite with Flyway migrations.
 *
 * @see https://github.com/PacificBiosciences/smrtflow/pull/126
 *
 * @author Jayson Falkner - jfalkner@pacificbiosciences.com
 */
class SqliteAndFlywayUsageSpec extends Specification {

  // force these tests to run sequentially since they can lock up the database
  sequential

  val db = new TestDatabase()

  "Connection pooling for H2" should {
    "be in use" in {
      db.dbUri startsWith "jdbc:h2:" must beTrue
    }
    "provide at least 2 Connection instances for Flyway migrations" in {
      val (conn1, conn2) = (db.connection, db.connection)
      try conn1 mustNotEqual conn2
      finally List.apply[Connection](conn1, conn2).foreach(x => x.close())
    }
  }
}

