package db.migration

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.flywaydb.core.api.callback.BaseFlywayCallback
import scala.slick.jdbc.UnmanagedSession
import scala.slick.driver.JdbcDriver.simple._
import java.sql.Connection

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
  def slickMigrate(implicit session: Session): Unit

  override final def migrate(conn: Connection): Unit = {
    val session = new UnmanagedSession(conn)
    try {
      session.withTransaction {
        slickMigrate(session)
      }
    } finally {
      session.close()
    }
  }

}

trait SlickCallback extends BaseFlywayCallback {

  def slickAfterBaseline(implicit session: Session): Unit

  override final def afterBaseline(conn: Connection): Unit = {
    val session = new UnmanagedSession(conn)
    try {
      session.withTransaction {
        slickAfterBaseline(session)
      }
    } finally {
      session.close()
    }
  }

  //... other flyway callback methods
}
