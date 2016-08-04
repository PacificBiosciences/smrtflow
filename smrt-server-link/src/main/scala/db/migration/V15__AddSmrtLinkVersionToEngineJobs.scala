package db.migration

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

/**
  * Created by mkocher on 8/4/16. First iteration to get the Version Propagated
  */
class V15__AddSmrtLinkVersionToEngineJobs extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run(DBIO.seq(
    sqlu"alter table engine_jobs add column smrtlink_version varchar(254);",
    sqlu"alter table engine_jobs add column smrtlink_tools_version varchar(254);"
  ))
}
