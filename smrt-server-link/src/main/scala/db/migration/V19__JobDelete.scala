
package db.migration

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future


class V19__JobDelete extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run(DBIO.seq(
    sqlu"alter table engine_jobs add column is_active boolean default 1;",
    sqlu"alter table datastore_files add column was_deleted boolean default 0;"
  ))
}
