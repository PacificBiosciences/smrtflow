package com.pacbio.secondary.smrtlink.database

import java.nio.file.Paths
import java.sql.Connection

import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import slick.util.AsyncExecutor
import slick.driver.SQLiteDriver.api.Database


/**
 * Abstraction for the Data Access Layer (DAL)
 *
 * This code abstracts a single point for queuing Slick DBIO actions via the `run()` method of the
 * db object. All other Data Access Objects (DAO), namely classes ending with "Dao" in the codebase
 * need to use this. SQL use outside of this DAL may result in SQLITE_BUSY errors (as long as we're
 * using SQLite).
 *
 * The code serves a few purposes.
 *
 * <ul>
 *   <li>Single point for queuing Slick DBIO actions</li>
 *   <li>Lazy-initialization of required DB directory (SQLite specific)</li>
 *   <li>Lazy-migration via Flyway (upon first use of DB)</li>
 *   <li>Connection pooling via DBCP, including caching PreparedStatements</li>
 *   <li>Flag for enabling Connection sharing in required cases. Required for SQLite since one
 *   Connection is available for use but the code sometimes requires multiple</li>
 *   <ul>
 *     <li>Flyway migrations lock the DB schema (one connection) then migration (second connection).</li>
 *     <li>Slick <code>DBIO.seq</code> use will open multiple connections</li>
 *   </ul>
 *   <li>A wrapper for DBIO Future instances to provide SQLite compatibility and debugging</li>
 * </ul>
 *
 * @param dbURI JDBC driver string. Typically configured by application.conf
 */
class Dal(val dbURI: String) {

  // flag for use when Flyway migrations running on SQLite
  var shareConnection: Boolean = false

  // DBCP for connection pooling and caching prepared statements for use in SQLite
  val connectionPool = new BasicDataSource() {
    // work-around for Flyway DB migrations needing 2 connections and SQLite supporting 1
    var cachedConnection: Connection = null

    override def getConnection(): Connection = {
      // recycle the connection only for the special use case of Flyway database migrations
      if (shareConnection && cachedConnection != null && cachedConnection.isValid(3000)) {
        return cachedConnection
      }
      else {
        // guard for SQLite use. also applicable in any case where only 1 connection is allowed
        if (cachedConnection != null && !cachedConnection.isClosed) {
          throw new RuntimeException("Can't have multiple sql connections open. An old connection may not have had close() invoked.")
        }
        cachedConnection = super.getConnection()
      }
      cachedConnection
    }
  }
  connectionPool.setDriverClassName("org.sqlite.JDBC")
  connectionPool.setUrl(dbURI)
  connectionPool.setInitialSize(1)
  connectionPool.setMaxTotal(1)
  // pool prepared statements
  connectionPool.setPoolPreparedStatements(true)
  // TODO: how many cursors can be left open? i.e. what to set for maxOpenPreparedStatements
  // enforce no auto-commit
  //connectionPool.setDefaultAutoCommit(false)
  //connectionPool.setEnableAutoCommitOnReturn(true)


  val flyway = new Flyway() {
    override def migrate(): Int = {
      try {
        shareConnection = true
        // lazy make directories as needed for sqlite
        if (dbURI.startsWith("jdbc:sqlite:")) {
          val file = Paths.get(dbURI.stripPrefix("jdbc:sqlite:"))
          if (file.getParent != null) {
            val dir = file.getParent.toFile
            if (!dir.exists()) dir.mkdirs()
          }
        }

        super.migrate()
      }
      finally {
        shareConnection = false
      }
    }
  }
  flyway.setDataSource(connectionPool)
  flyway.setBaselineOnMigrate(true)
  flyway.setBaselineVersionAsString("1")

  // -1 queueSize means unlimited. This probably needs to be tuned
  lazy val realDb = Database.forDataSource(connectionPool, executor = AsyncExecutor("db-executor", 1, -1))
  // wrap the Dal to make a db that'll share connections
  val db = new DatabaseWrapper(this)
}