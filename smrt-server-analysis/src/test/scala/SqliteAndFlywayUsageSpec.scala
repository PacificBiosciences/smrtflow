import java.sql.Connection
import java.util

import com.pacbio.secondary.analysis.configloaders.PbsmrtpipeConfigLoader
import com.pacbio.secondary.smrtlink.actors.{DalProvider, JobsDaoProvider, TestDalProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
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
class SqliteAndFlywayUsageSpec
  extends Specification
  with PbsmrtpipeConfigLoader
  with SmrtLinkConfigProvider
  with DalProvider
  with JobsDaoProvider
  with TestDalProvider{

  // force these tests to run sequentially since they can lock up the database
  sequential

  "Connection pooling for SQLite" should {
    "be in use" in {
      jobsDao().dal.dbURI startsWith "jdbc:sqlite:" must beTrue
    }
    "share Connection instances during Flyway migrations" in {
      val dao = jobsDao()
      dao.dal.migrating = true
      val conn1 = dao.dal.connectionPool.getConnection
      val conn2 = dao.dal.connectionPool.getConnection
      try conn1 mustEqual conn2
      finally List.apply[Connection](conn1, conn2).foreach(x => x.close())
    }
    "guard against failing to close Connection instances" in {
      val dao = jobsDao()
      dao.dal.migrating = false
      val conn1 = dao.dal.connectionPool.getConnection
      try dao.dal.connectionPool.getConnection() must throwA[RuntimeException]
      finally conn1.close()
    }
    "return unique Connection instances during normal, non-migration use" in {
      val dao = jobsDao()
      dao.dal.migrating = false
      val conn1 = dao.dal.connectionPool.getConnection
      conn1.close()
      val conn2 = dao.dal.connectionPool.getConnection
      conn2.close()
      conn1 mustNotEqual conn2
    }
  }
}
