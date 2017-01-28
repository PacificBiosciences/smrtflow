package db.migration


import com.pacbio.secondary.smrtlink.database.TableModels
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

// scalastyle:off
/**
  * First Postgresql Migration
  *
  * FIXME(mpkocher)(2016-12-9) copy Table models into here
  *
  * The current TableModels are imported directly while the schemas are iterated on.
  * Once this stablizes, the tables models should be copied in here and the case class models removed to decouple
  * the code from future model changes.
  *
  *
  */
class V1__InitialSchema extends JdbcMigration with SlickMigration with LazyLogging {

  // Note, the project name is a unique identifier
  val project = Project(1, "General Project", "General SMRT Link project. By default all imported datasets and analysis jobs will be assigned to this project", ProjectState.CREATED, JodaDateTime.now(), JodaDateTime.now(), isActive = true)
  // This is "admin" from the wso2 model
  val projectUser = ProjectUser(project.id, "admin", ProjectUserRole.OWNER)

  // Is this even used?
  val datasetTypeDatum = List(
    ("PacBio.DataSet.ReferenceSet", "Display name for PacBio.DataSet.ReferenceSet", "Description for PacBio.DataSet.ReferenceSet", JodaDateTime.now(), JodaDateTime.now(), "references"),
    ("PacBio.DataSet.ConsensusReadSet", "Display name for PacBio.DataSet.ConsensusReadSet", "Description for PacBio.DataSet.ConsensusReadSet", JodaDateTime.now(), JodaDateTime.now(), "ccsreads"),
    ("PacBio.DataSet.ContigSet", "Display name for PacBio.DataSet.ContigSet", "Description for PacBio.DataSet.ContigSet", JodaDateTime.now(), JodaDateTime.now(), "contigs"),
    ("PacBio.DataSet.SubreadSet", "Display name for PacBio.DataSet.SubreadSet", "Description for PacBio.DataSet.SubreadSet", JodaDateTime.now(), JodaDateTime.now(), "subreads"),
    ("PacBio.DataSet.BarcodeSet", "Display name for PacBio.DataSet.BarcodeSet", "Description for PacBio.DataSet.BarcodeSet", JodaDateTime.now(), JodaDateTime.now(), "barcodes"),
    ("PacBio.DataSet.ConsensusAlignmentSet", "Display name for PacBio.DataSet.ConsensusAlignmentSet", "Description for PacBio.DataSet.ConsensusAlignmentSet", JodaDateTime.now(), JodaDateTime.now(), "ccsalignments"),
    ("PacBio.DataSet.HdfSubreadSet", "Display name for PacBio.DataSet.HdfSubreadSet", "Description for PacBio.DataSet.HdfSubreadSet", JodaDateTime.now(), JodaDateTime.now(), "hdfsubreads"),
    ("PacBio.DataSet.AlignmentSet", "Display name for PacBio.DataSet.AlignmentSet", "Description for PacBio.DataSet.AlignmentSet", JodaDateTime.now(), JodaDateTime.now(), "alignments")
  )

  // This has been pulled into the code. Not sure this was a great idea.
  // It strongly couples the version of the code with the db schema.
  final val jobStatesDatum = List(
    (1, "CREATED", "State CREATED description", JodaDateTime.now(), JodaDateTime.now()),
    (2, "SUBMITTED", "State SUBMITTED description", JodaDateTime.now(), JodaDateTime.now()),
    (3, "RUNNING", "State RUNNING description", JodaDateTime.now(), JodaDateTime.now()),
    (4, "TERMINATED", "State TERMINATED description", JodaDateTime.now(), JodaDateTime.now()),
    (5, "SUCCESSFUL", "State SUCCESSFUL description", JodaDateTime.now(), JodaDateTime.now()),
    (6, "FAILED", "State FAILED description", JodaDateTime.now(), JodaDateTime.now()),
    (7, "UNKNOWN", "State UNKNOWN description", JodaDateTime.now(), JodaDateTime.now())
  )

  override def slickMigrate(db: DatabaseDef): Future[Any] = {

    db.run(DBIO.seq(
      TableModels.schema.create,
      sqlu"create unique index project_name_unique on projects (name) where is_active;",
      TableModels.datasetMetaTypes ++= datasetTypeDatum.map(ServiceDataSetMetaType.tupled),
      // TableModels.jobStates ++= finalJobStates
      TableModels.projects += project,
      TableModels.projectsUsers += projectUser
    ))
  }

}
