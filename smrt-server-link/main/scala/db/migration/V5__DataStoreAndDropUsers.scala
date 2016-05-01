package db.migration

import java.sql.SQLException
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.{GetResult, StaticQuery => Q}

class V5__DataStoreAndDropUsers extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(implicit session: Session) {
    session.withTransaction {
      Q.updateNA("""
alter table datastore_files add column "name" varchar(254) not null default ""
""").execute

      Q.updateNA("""
alter table datastore_files add column "description" varchar(254) not null default ""
""").execute

      Q.updateNA("""
drop table users
""").execute
    }
  }
}

