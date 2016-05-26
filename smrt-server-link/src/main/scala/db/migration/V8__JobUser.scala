package db.migration

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

import slick.driver.SQLiteDriver.api._

class V8__JobUser extends JdbcMigration with SlickMigration {
  override def slickMigrate: DBIOAction[Any, NoStream, Nothing] =
    SimpleDBIO {
     _.connection.prepareCall("""
alter table engine_jobs add column "created_by" varchar(254)
""").execute
    }
}
