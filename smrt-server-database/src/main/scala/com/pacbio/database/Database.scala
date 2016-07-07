package com.pacbio.database

import java.nio.file.Paths
import java.sql.Connection

import db.migration.h2.V15__WriteDataToH2
import db.migration.sqlite._
import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.flywaydb.core.api.{MigrationType, MigrationVersion}
import org.flywaydb.core.api.resolver.{ResolvedMigration, MigrationResolver}
import org.flywaydb.core.internal.resolver.ResolvedMigrationImpl
import org.flywaydb.core.internal.resolver.jdbc.JdbcMigrationExecutor
import org.flywaydb.core.internal.util.ClassUtils
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
  // flag for use when Flyway migrations running on SQLite
  protected var shareConnection: Boolean = false
  // flag for tracking debugging and timing info
  var debug: Boolean = true
  val listeners: List[DatabaseListener] =
    if (System.getProperty("PACBIO_DATABASE") == null)
      List()
    else
      List(new LoggingListener(), new ProfilingListener())

  // DBCP for connection pooling and caching prepared statements
  protected val connectionPool = new BasicDataSource()
  connectionPool.setDriverClassName("org.h2.Driver")
  connectionPool.setUrl(dbUri)
  connectionPool.setInitialSize(4)
  connectionPool.setMaxTotal(8)
  // pool prepared statements
  connectionPool.setPoolPreparedStatements(true)
  // TODO: how many cursors can be left open? i.e. what to set for maxOpenPreparedStatements

  // eagerly run migrations
  Await.ready(Future(migrate()), 1.minute)

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
        legacySqliteURI.foreach { uri =>
          val connectionPool = new BasicDataSource() {
            // work-around for Flyway DB migrations needing 2 connections and SQLite supporting 1
            var cachedConnection: Connection = null

            override def getConnection: Connection = {
              // recycle the connection only for the special use case of Flyway database migrations
              if (shareConnection && cachedConnection != null && cachedConnection.isValid(3000)) {
                return cachedConnection
              }
              else {
                // guard for SQLite use. also applicable in any case where only 1 connection is allowed
                if (cachedConnection != null && !cachedConnection.isClosed) {
                  throw new RuntimeException("Can't have multiple sql connections open. An old connection may not have had close() invoked.")
                }
                cachedConnection = super.getConnection
              }
              cachedConnection
            }
          }
          connectionPool.setDriverClassName("org.sqlite.JDBC")
          connectionPool.setUrl(uri)
          connectionPool.setInitialSize(1)
          connectionPool.setMaxTotal(1)
          connectionPool.setPoolPreparedStatements(true)

          val flyway = new Flyway() {
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

          val sqliteResolver = new PredefMigrationResolver(
            flyway.getClassLoader,
            classOf[V1__InitialSchema],
            classOf[V2__ProjectEndpoint],
            classOf[V3__CollectionMetadata],
            classOf[V4__RunService],
            classOf[V5__DataStoreAndDropUsers],
            classOf[V6__OptionalRunFields],
            classOf[V7__Sample],
            classOf[V8__JobUser],
            classOf[V9__CollectionPathUri],
            classOf[V10__DropJobStatesTable],
            classOf[V11__AddIndexes],
            classOf[V12__GmapReferences],
            classOf[V13__MoreDatasets],
            classOf[V14__ReadDataForH2]
          )

          flyway.setLocations("db/migration/sqlite")
          flyway.setResolvers(sqliteResolver)
          flyway.setSkipDefaultResolvers(true)
          flyway.setDataSource(connectionPool)
          flyway.setBaselineOnMigrate(true)
          flyway.setBaselineVersionAsString("1")
          flyway.migrate()
        }

        val flyway = new Flyway()
        val h2Resolver = new PredefMigrationResolver(flyway.getClassLoader, classOf[V15__WriteDataToH2])
        flyway.setLocations("db/migration/h2")
        flyway.setResolvers(h2Resolver)
        flyway.setSkipDefaultResolvers(true)
        flyway.setDataSource(connectionPool)
        flyway.setBaselineOnMigrate(true)
        flyway.setBaselineVersionAsString("15")
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

class PredefMigrationResolver(classLoader: ClassLoader, migrations: Class[_]*) extends MigrationResolver {
  import scala.collection.JavaConversions._

  override def resolveMigrations(): java.util.Collection[ResolvedMigration] = {
    migrations.map { m =>
      val j = m.asSubclass(classOf[JdbcMigration])
      val r = new ResolvedMigrationImpl

      // Assume class is named V123__Foo
      val name = j.getSimpleName
      val verAndDesc = name.split("__")
      val ver = MigrationVersion.fromVersion(verAndDesc(0).substring(1))
      val desc = verAndDesc(1)

      r.setVersion(ver)
      r.setDescription(desc)
      r.setScript(j.getName)
      r.setChecksum(null)
      r.setType(MigrationType.JDBC)
      r.setPhysicalLocation(ClassUtils.getLocationOnDisk(j))
      r.setExecutor(new JdbcMigrationExecutor(ClassUtils.instantiate(j.getName, classLoader)))
      r
    }
  }
}