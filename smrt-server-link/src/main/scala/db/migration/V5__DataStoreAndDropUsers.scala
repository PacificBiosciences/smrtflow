package db.migration

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.SQLiteDriver.api._

class V5__DataStoreAndDropUsers extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate: DBIOAction[Any, NoStream, Nothing] =
    SimpleDBIO { b =>
      b.connection.prepareCall("""
alter table datastore_files add column "name" varchar(254) not null default ""
""").execute

      b.connection.prepareCall("""
alter table datastore_files add column "description" varchar(254) not null default ""
""").execute

      b.connection.prepareCall("""
drop table users
""").execute
    }
}

