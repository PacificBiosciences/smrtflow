package db.migration

import scala.concurrent.Future

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import org.flywaydb.core.api.migration.jdbc.JdbcMigration


class V21__AddEula extends JdbcMigration with SlickMigration{

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(DBIO.seq(
      sqlu"""CREATE TABLE IF NOT EXISTS "eula_record" ("user" VARCHAR(254) NOT NULL, "os_version" VARCHAR(254) NOT NULL,"accepted_at" INTEGER NOT NULL, "smrtlink_version" VARCHAR(254) PRIMARY_KEY NOT NULL, "state" INTEGER NOT NULL DEFAULT 0)"""
    ))
  }
}
