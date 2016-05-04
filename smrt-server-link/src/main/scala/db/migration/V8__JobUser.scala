package db.migration

import java.sql.SQLException
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.{GetResult, StaticQuery => Q}

class V8__JobUser extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(implicit session: Session) {
    session.withTransaction {
      Q.updateNA("""
alter table engine_jobs add column "created_by" varchar(254)
""").execute
    }
  }
}
