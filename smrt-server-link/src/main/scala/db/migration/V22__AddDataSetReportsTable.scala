package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.PostgresProfile.api._

import slick.jdbc.JdbcBackend.DatabaseDef

import com.pacbio.secondary.smrtlink.database.TableModels

import scala.concurrent.Future

class V22__AddDataSetReportsTable
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] =
    db.run(TableModels.datasetReports.schema.create)

}
