package db.migration

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

class V8__JobUser extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run {
    // scalastyle:off
    SimpleDBIO {
      _.connection.prepareCall(
        """
alter table engine_jobs add column "created_by" varchar(254)
""").execute
    }
  }
}
