package db.migration


import scala.concurrent.Future

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import org.flywaydb.core.api.migration.jdbc.JdbcMigration


/**
  * Created by mkocher on 10/17/16.
  *
  * This is to sync up the missing V7 migration from perforce->github migration during the 3.1.1 release
  *
  */
class V18__AddSample extends JdbcMigration with SlickMigration{

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(DBIO.seq(
      sqlu"""CREATE TABLE IF NOT EXISTS "SAMPLE" ("DETAILS" VARCHAR(254) NOT NULL,"UNIQUE_ID" BLOB PRIMARY KEY NOT NULL,"NAME" VARCHAR(254) NOT NULL,"CREATED_BY" VARCHAR(254) NOT NULL,"CREATED_AT" INTEGER NOT NULL)"""
    ))
  }
}
