package com.pacbio.database

import java.nio.file.Paths
import java.sql.Connection

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.SQLiteDriver.api.{Database => SQLiteDatabase}
import slick.util.AsyncExecutor

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global


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
 */
class Database(dbURI: String) extends LazyLogging {

  val dbUri: String = dbURI
  // flag for indicating if migrations are complete
  protected var migrationsComplete: Boolean = false
  // flag for use when Flyway migrations running on SQLite
  protected var shareConnection: Boolean = false
  // flag for tracking debugging and timing info
  var debug: Boolean = false

  // DBCP for connection pooling and caching prepared statements for use in SQLite
  protected val connectionPool = new BasicDataSource() {
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
  connectionPool.setUrl(dbUri)
  connectionPool.setInitialSize(1)
  connectionPool.setMaxTotal(1)
  // pool prepared statements
  connectionPool.setPoolPreparedStatements(true)
  // TODO: how many cursors can be left open? i.e. what to set for maxOpenPreparedStatements
  // enforce no auto-commit
  //connectionPool.setDefaultAutoCommit(false)
  //connectionPool.setEnableAutoCommitOnReturn(true)

  private val flyway = new Flyway() {
    override def migrate(): Int = {
      try {
        shareConnection = true
        // lazy make directories as needed for sqlite
        if (dbUri.startsWith("jdbc:sqlite:") && dbUri != "jdbc:sqlite:") {
          val file = Paths.get(dbUri.stripPrefix("jdbc:sqlite:"))
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

  // keep access to the real database restricted. we don't want atyptical use in the codebase
  // TODO: -1 queueSize means unlimited. This probably needs to be tuned
  private lazy val db = SQLiteDatabase.forDataSource(
    connectionPool,
    executor = AsyncExecutor("db-executor", 1, -1))


  /**
   * Force database migrations
   *
   * This method need not be explicitly invoked during normal usage. It will be lazy invoked by
   * the <code>run()</code> method. The method is exposed to support situations where code needs
   * to assert that database migrations have run. e.g. test cases.
   */
  def migrate(): Unit = {
    this.synchronized {
      if (!migrationsComplete) {
        flyway.migrate()
        migrationsComplete = true
      }
    }
  }

  /**
   * Runs DBIOAction instances on the underlying SQLite RDMS
   *
   * This is the main contract for the entire codebase's use of the underlying SQLite database. It
   * serves a few important needs.
   *
   * <ul>
   *   <li>Lazy running of migrations via <code>migrate()</code></li>
   *   <li>Enforce that the rest of the codebase can't access the DB outside of this method</li>
   *   <li>Share Connection instances for edge cases that require multiple Connection objects and
   *   would otherwise cause SQLite to lockup during use. e.g. Flyway migrations and DBIO.seq use.</li>
   *   <li>Exposes debugging and timing information about RDMS use, if needed</li>
   * </ul>
   * @param a
   * @tparam R
   * @return
   */
  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = {
    // lazy migrate via Flyway
    if (!migrationsComplete) migrate()

    // local copy of the debug flag in case state changes
    val dbug = debug
    // shared prefix so that log messages can be easily grep'd
    val Prefix = "[PacBio:Database] "

    // track timing for queue wait and RBMS execution
    val start: Long = if (dbug) System.currentTimeMillis() else 0
    // track what code is doing DB access (stack trace outside of the execution context)
    val stacktrace = if(dbug) new Exception().getStackTrace else null
    Future[R] {
      try {
        // toggle on connection sharing so that Flyway migrations and DBIO.seq are supported
        shareConnection = true
        // track RMDS execution timing
        val startRDMS: Long = if (dbug) System.currentTimeMillis() else 0
        // run the SQL and wait for it is execute
        val f = db.run(a)
        val toreturn = Await.result(f, Duration.Inf)
        // track RDBMS execution timing
        val endRDMS: Long = if (dbug) System.currentTimeMillis() else 0
        if (dbug) {
          logger.debug(s"$Prefix RDMS executed x in ${endRDMS - startRDMS} ms")

          // tool the future to dump failure reasons
          f onFailure {
            case t => logger.error(s"$Prefix RDMS error", t)
          }
        }
        toreturn
      }
      finally {
        val end: Long = if (dbug) System.currentTimeMillis() else 0
        // track timing for quere and RDMS execution
        if (dbug) logger.debug(s"$Prefix total timing for x was ${end - start}")
        // teardown the connection and flag off connection sharing
        shareConnection = false
        val conn = connectionPool.cachedConnection
        if (conn != null && !conn.isClosed) {
          if (dbug) {
            logger.debug(s"$Prefix connection left open. Running commit() and close()")
          }
          conn.commit()
          conn.close()
        }
      }
    }
  }
}