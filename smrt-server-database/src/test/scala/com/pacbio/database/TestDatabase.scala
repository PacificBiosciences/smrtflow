package com.pacbio.database

import java.sql.Connection

/**
 * Exposes needed Database fields testing
 *
 * Database exposes the minimal contract for use by the codebase. Much of the internal state is
 * hidden and should not be directly used outside of tests. This class is included with the test
 * code and exposes needed state to confirm that connection sharing and the multi-connection
 * guard work as expected.
 *
 * @author Jayson Falkner - jfalkner@pacificbiosciences.com
 */
class TestDatabase (dbURI: String = "jdbc:sqlite:") extends Database(dbURI) {

  override def dbUri: String = super.dbUri

  def setShareConnection(b: Boolean): Unit = shareConnection = b

  def connection: Connection = connectionPool.getConnection()



}
