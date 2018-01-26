package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.PostgresProfile.api._

import slick.jdbc.JdbcBackend.DatabaseDef

import com.pacbio.secondary.smrtlink.database.legacy.BaseLine

import scala.concurrent.Future

// scalastyle:off
class V1__InitialSchema
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {

    db.run(
      DBIO.seq(
        BaseLine.schema.create,
        BaseLine.datasetMetaTypes ++= BaseLine.coreDataSetMetaTypes,
        BaseLine.projects += BaseLine.generalProject,
        BaseLine.projectsUsers += BaseLine.projectUser
      ))
  }

}
