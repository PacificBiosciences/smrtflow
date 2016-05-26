package db.migration

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.flywaydb.core.api.callback.BaseFlywayCallback
import java.sql.Connection

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.{BaseSession, DatabaseDef}
import slick.jdbc.JdbcDataSource
import slick.util.AsyncExecutor

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// UnmanagedSession was deprecated in Slick 3.0 and removed in 3.1. This is my attempt to re-create its functionality
// for 3.1.

class UnmanagedJdbcDataSource(conn: Connection) extends JdbcDataSource {
  override def createConnection() = conn
  override def close() = ()
}

class UnmanagedSession(database: DatabaseDef) extends BaseSession(database) {
  override def close() = ()
}

class UnmanagedDatabase(conn: Connection)
  extends DatabaseDef(new UnmanagedJdbcDataSource(conn), AsyncExecutor("UmanagedDatabase-AsyncExecutor", 1, -1)) {

  override def createSession() = new UnmanagedSession(this)
}

/**
 * By including this trait in your Flyway JdbcMigration, you only
 * need to provide an implementation for this the slick_migrate
 * method, which accepts a Slick session.
 *
 * Given that Session, you can perform any Slick operation, like
 * creating or modifying a table, populating the database, etc.
 *
 */
trait SlickMigration { self: JdbcMigration =>

  // Implement this in your subclass
  def slickMigrate: DBIOAction[Any, NoStream, Nothing]

  override final def migrate(conn: Connection): Unit = {
    val db = new UnmanagedDatabase(conn)
    try {
      Await.ready(db.run(slickMigrate.transactionally), Duration.Inf)
    } finally {
      db.close()
    }
  }
}

trait SlickCallback extends BaseFlywayCallback {

  def slickAfterBaseline: DBIOAction[Any, NoStream, Nothing]

  override final def afterBaseline(conn: Connection): Unit = {
    val db = new UnmanagedDatabase(conn)
    try {
      db.run(slickAfterBaseline.transactionally)
    } finally {
      db.close()
    }
  }

  //... other flyway callback methods
}
