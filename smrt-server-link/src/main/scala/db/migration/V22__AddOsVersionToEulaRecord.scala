package db.migration

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

class V22__AddOsVersionToEulaRecord extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run(DBIO.seq(
    sqlu"alter table eula_record add column os_version varchar(254);"
  ))
}
