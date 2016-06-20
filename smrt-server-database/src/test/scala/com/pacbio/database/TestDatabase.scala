package com.pacbio.database

import java.sql.Connection

/**
 * Exposes needed Database fields testing
 *
 * Database exposes the minimal contract for use by the codebase. Much of the internal state is
 * hidden and should not be directly used outside of tests. This class is included with the test
 * code and exposes needed state to confirm that multiple connections are supported.
 *
 * When SQLite was in use, this test also exercised a few of the edge cases; however, the codebase
 * no longer needs to do those checks with H2 and Postgres.
 *
 * @author Jayson Falkner - jfalkner@pacificbiosciences.com
 */
class TestDatabase (dbURI: String = "jdbc:h2:mem:") extends Database(dbURI) {

  override def dbUri: String = super.dbUri

  def connection: Connection = connectionPool.getConnection()



}
