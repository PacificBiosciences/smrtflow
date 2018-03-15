package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._
import spray.json._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class V19__AddSubJobTypeIdToEngineJob
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  case class UpdateSubJobType(id: Int, subJobTypeId: Option[String])

  private def extractPipelineIdFromJsonSettings(sx: String): Option[String] =
    sx.parseJson.asJsObject.getFields("pipelineId") match {
      case Seq(JsString(value)) => Some(value)
      case _ => None
    }

  private def convert(jobId: Int, settings: String): UpdateSubJobType = {
    def toPipeline(sx: String): Option[String] =
      Try(extractPipelineIdFromJsonSettings(settings)).toOption.flatten

    UpdateSubJobType(jobId, toPipeline(settings))
  }

  def updateSubJobId(u: UpdateSubJobType): DBIO[Int] =
    sqlu"UPDATE engine_jobs SET sub_job_type_id = '${u.subJobTypeId}' WHERE job_id = ${u.id}"

  def updateSubJobTypeIds(records: Seq[UpdateSubJobType]): DBIO[Seq[Int]] =
    DBIO.sequence(records.map(updateSubJobId))

  def getRecords: DBIO[Seq[(Int, String)]] =
    sql"""SELECT job_id, json_settings FROM engine_jobs WHERE job_type_id = 'pbsmrtpipe'"""
      .as[(Int, String)]

  override def slickMigrate(db: DatabaseDef): Future[Any] = {

    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE engine_jobs ADD COLUMN sub_job_type_id VARCHAR(256) DEFAULT NULL""",
        for {
          records <- getRecords
          _ <- updateSubJobTypeIds(records.map(x => convert(x._1, x._2)))
        } yield s"Successfully updated ${records.length}"
      ))
  }

}
