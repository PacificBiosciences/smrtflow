package com.pacbio.database

import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.H2Driver.api.{Database => H2Database}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import ExecutionContext.Implicits.global

/**
 * Abstraction for the Data Access Layer (DAL)
 *
 * This code abstracts a single point for queuing Slick DBIO actions via the `run()` method of the
 * db object. All other Data Access Objects (DAO), namely classes ending with "Dao" in the codebase
 * need to use this. SQL use outside of this contract should be avoided.
 *
 * The code serves a few purposes.
 *
 * <ul>
 *   <li>Single point for queuing Slick DBIO actions</li>
 *   <li>Lazy-initialization of required DB directory (SQLite specific)</li>
 *   <li>Lazy-migration via Flyway (upon first use of DB)</li>
 *   <li>Connection pooling via DBCP, including caching PreparedStatements</li>
 *   <li>A wrapper for DBIO Future instances to provide debugging</li>
 * </ul>
 *
 */
class Database(dbURI: String, legacySqliteURI: Option[String] = None) {

  def dbUri: String = dbURI
  // flag for indicating if migrations are complete
  protected var migrationsComplete: Boolean = false
  // flag for tracking debugging and timing info
  var debug: Boolean = true
  val listeners: List[DatabaseListener] =
    if (System.getProperty("PACBIO_DATABASE") == null)
      List()
    else
      List(new LoggingListener(), new ProfilingListener())

  legacySqliteURI.foreach { uri =>
    val connectionPool = new BasicDataSource()
    connectionPool.setDriverClassName("org.sqlite.JDBC")
    connectionPool.setUrl(uri)
    connectionPool.setInitialSize(1)
    connectionPool.setMaxTotal(1)
    connectionPool.setPoolPreparedStatements(true)

    val flyway = new Flyway()

    flyway.setLocations("db/migration/sqlite")
    flyway.setDataSource(connectionPool)
    flyway.setBaselineOnMigrate(true)
    flyway.migrate()
  }

  // DBCP for connection pooling and caching prepared statements
  protected val connectionPool = new BasicDataSource()
  connectionPool.setDriverClassName("org.h2.Driver")
  connectionPool.setUrl(dbUri)
  connectionPool.setInitialSize(4)
  connectionPool.setMaxTotal(8)
  // pool prepared statements
  connectionPool.setPoolPreparedStatements(true)
  // TODO: how many cursors can be left open? i.e. what to set for maxOpenPreparedStatements


  private val flyway = new Flyway()
  flyway.setLocations("db/migration/h2")
  flyway.setDataSource(connectionPool)
  flyway.setBaselineOnMigrate(true)
  flyway.setBaselineVersionAsString("2.1")
  // eagerly run migrations
  migrate()

  // keep access to the real database restricted. we don't want atyptical use in the codebase
  // TODO: -1 queueSize means unlimited. This probably needs to be tuned
  private lazy val db = H2Database.forDataSource(connectionPool)

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
   * Runs DBIOAction instances on the underlying RDBMS
   *
   * This is the main contract for the entire codebase's use of the underlying database. It
   * serves a few important needs.
   *
   * <ul>
   *   <li>Lazy running of migrations via <code>migrate()</code></li>
   *   <li>Enforce that the rest of the codebase can't access the DB outside of this method</li>
   *   <li>Exposes debugging and timing information about RDBMS use, if needed</li>
   * </ul>
   *
   * @param a
   * @tparam R
   * @return
   */
  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = {
    // lazy migrate via Flyway
    if (!migrationsComplete) migrate()

    val dbug = !listeners.isEmpty

    // track timing for queue wait and RBMS execution
    val start: Long = if (dbug) System.currentTimeMillis() else 0
    // track what code is doing DB access (stack trace outside of the execution context)
    val stacktrace = if (dbug) new Exception("RDMS Query Tracking") else null
    val code = if (dbug) stacktrace.getStackTrace()(1).toString else null
    if (dbug) Future { listeners.foreach(_.create(code, stacktrace)) }
    // main wrapper for running the query and blocking with timeout for detecting SQLite related issues
    Future[R] {
      try {
        // tally how many db.run() are being invoked
        if (dbug) Future { listeners.foreach(_.start(code, stacktrace)) }
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
        val toreturn = Await.result(f, 12345 milliseconds) // TODO: config via param
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
        if (dbug) Future { listeners.foreach(_.end(code, stacktrace)) }
        if (dbug) Future { listeners.foreach(_.end(code, stacktrace)) }
        val end: Long = if (dbug) System.currentTimeMillis() else 0
        // track timing for queue and RDMS execution
        if (dbug) Future { listeners.foreach(_.allDone(start, end, code, stacktrace)) }
      }
    }
  }
}