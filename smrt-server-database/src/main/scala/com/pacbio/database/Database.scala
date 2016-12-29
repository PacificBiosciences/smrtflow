package com.pacbio.database

import java.nio.file.Paths
import java.sql.Connection
import java.util.concurrent.Executors

import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.SQLiteDriver.api.{Database => SQLiteDatabase}
import slick.util.AsyncExecutor

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import ExecutionContext.Implicits.global

import scala.language.postfixOps

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
class Database(dbURI: String) {

  def dbUri: String = dbURI
  // flag for indicating if migrations are complete
  protected var migrationsComplete: Boolean = false
  // flag for use when Flyway migrations running on SQLite
  protected var shareConnection: Boolean = false
  // flag for tracking debugging and timing info
  var debug: Boolean = true
  val listeners: List[DatabaseListener] =
    if (System.getProperty("PACBIO_DATABASE") == null)
      List()
    else
      List(new LoggingListener(), new ProfilingListener())
  protected var nMigrationsApplied: Int = 0

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
  // eagerly run migrations
  migrate()

  // keep access to the real database restricted. we don't want atyptical use in the codebase
  // TODO: -1 queueSize means unlimited. This probably needs to be tuned
  private lazy val db = SQLiteDatabase.forDataSource(
    connectionPool,
    executor = AsyncExecutor("db-executor", 1, -1))

  // special execution context for wrapped, serial DB access
  val dec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

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
        nMigrationsApplied = flyway.migrate()
        migrationsComplete = true
      }
    }
  }

  def migrationsApplied(): Int = nMigrationsApplied


  var queryCount = 0

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
   *
   * @param a
   * @tparam R
   * @return
   */
  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = {
    // lazy migrate via Flyway
    if (!migrationsComplete) migrate()

    val dbug = listeners.nonEmpty

    // track timing for queue wait and RBMS execution
    val start: Long = if (dbug) System.currentTimeMillis() else 0
    // track what code is doing DB access (stack trace outside of the execution context)
    val stacktrace = if (dbug) new Exception("RDMS Query Tracking") else null
    val code = if (dbug) stacktrace.getStackTrace()(1).toString else null
    if (dbug) Future { listeners.foreach(_.create(code, stacktrace)) }
    // main wrapper for running the query and blocking with timeout for detecting SQLite related issues
    Future[R] {
      try {
        // tally how many db.run() are being invoked -- TODO: can remove queryCount entirely?
        queryCount += 1
        if (dbug) Future { listeners.foreach(_.start(code, stacktrace, queryCount)) }
        // track RMDS execution timing
        val startRDMS: Long = if (dbug) System.currentTimeMillis() else 0
        // run the SQL and wait for it is execute
        val f = db.run(a)
        if (dbug) {
          // tool the future to dump failure reasons
          f onFailure {
            case t => Future { listeners.foreach(_.error(code, stacktrace, t)) }
          }
          // tool the future to dump failure reasons
          f onSuccess {
            case x => Future { listeners.foreach(_.success(code, stacktrace, x)) }
          }
        }
        val toreturn = Await.result(f, 23456 milliseconds) // TODO: config via param
        // track RDBMS execution timing
        val endRDMS: Long = if (dbug) System.currentTimeMillis() else 0
        if (dbug) Future { listeners.foreach(_.dbDone(startRDMS, endRDMS, code, stacktrace)) }
        toreturn
      }
      catch {
        case t : Throwable => {
          if (dbug) Future {
            listeners.foreach(_.timeout(code, stacktrace, new Exception("Nested db.run() calls?", t)))
          }
          throw t
        }
      }
      finally {
        // decrement query count
        queryCount -= 1
        if (dbug) Future { listeners.foreach(_.end(code, stacktrace, queryCount)) }

        val end: Long = if (dbug) System.currentTimeMillis() else 0
        // track timing for queue and RDMS execution
        if (dbug) Future { listeners.foreach(_.allDone(start, end, code, stacktrace)) }
        // only force close if all (sub-)queries are done
        if (queryCount == 0) {
          val conn = connectionPool.cachedConnection
          if (!conn.isClosed)
            try {
              conn.close()
            } catch {
              case ex: Throwable => println("Ignoring commit()/close() fail: " + ex)
            }
        }
      }
      // share the execution context the DB uses
    } (dec)
  }
}
