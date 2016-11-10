package db.migration

import scala.concurrent.Future

import org.joda.time.{DateTime => JodaDateTime}
import com.typesafe.scalalogging.LazyLogging

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat

class V21__AddEula extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run {
    V21Schema.eulas.schema.create
  }
}

object V21Schema extends PacBioDateTimeDatabaseFormat {
  class EulaRecordT(tag: Tag) extends Table[(String, JodaDateTime, String, Boolean, Boolean)](tag, "eula_record") {
    def user: Rep[String] = column[String]("user")
    def acceptedAt: Rep[JodaDateTime] = column[JodaDateTime]("accepted_at")
    def smrtlinkVersion: Rep[String] = column[String]("smrtlink_version")
    def enableInstallMetrics: Rep[Boolean] = column[Boolean]("enable_install_metrics")
    def enableJobMetrics: Rep[Boolean] = column[Boolean]("enable_job_metrics")
    def * :ProvenShape[(String, JodaDateTime, String, Boolean, Boolean)] = (user, acceptedAt, smrtlinkVersion, enableInstallMetrics, enableJobMetrics)
  }

  lazy val eulas = TableQuery[EulaRecordT]
}
