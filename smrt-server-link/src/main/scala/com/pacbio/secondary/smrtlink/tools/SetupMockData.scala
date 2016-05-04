package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.jobs.JobModels.{JobEvent, EngineJob}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.jobs.{SimpleUUIDJobResolver, AnalysisJobStates}
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils

import org.joda.time.{DateTime => JodaDateTime}

import scala.slick.driver.SQLiteDriver.simple._
import scala.util.Random


trait SetupMockData extends MockUtils with InitializeTables {

  def runSetup(dao: JobsDao): Unit = {
    println(s"Created database connection from URI ${dao.dal.dbURI}")

    createTables
    loadBaseMock

    dao.dal.db.withSession { implicit session =>

      println("Inserting mock data")

      insertMockSubreadDataSetsFromDir()
      insertMockHdfSubreadDataSetsFromDir()
      insertMockReferenceDataSetsFromDir()
      insertMockAlignmentDataSets()

      // Jobs
      insertMockJobs()
      insertMockJobEvents()
      insertMockJobsTags()

      // datastore
      insertMockDataStoreFiles()

      println("Completed inserting mock data.")
    }
  }
}

/**
 * clumsy way to setup the test db
 */
trait MockUtils extends LazyLogging{

  import com.pacbio.secondary.smrtlink.database.TableModels._

  val dao: JobsDao

  // For Mock importing of data
  val _MOCK_DS_VERSION = "0.5.0"
  val _MOCK_NJOBS = 5
  val _MOCK_NDATASETS = 5
  val _MOCK_WORKFLOW_TEMPLATES = 5
  val _ROOT_MOCK_DATA_DIR = "/mock-datasets"
  val _MOCK_USER_ID = 1
  val _MOCK_PROJECT_ID = 1
  val _MOCK_JOB_ID = 1

  def toMd5(text: String): String = MessageDigest.getInstance("MD5").digest(text.getBytes).map("%02x".format(_)).mkString

  def getMockDataSetFiles(dirName: String): Seq[File] = {
    val u = getClass.getResource(_ROOT_MOCK_DATA_DIR)
    val p = Paths.get(u.toURI)
    val f = p.resolve(dirName).toFile
    val files = f.listFiles.toList
    println(s"Loading mock data from $f. Found ${files.length} mock files.")
    files
  }

  def insertMockJobs()(implicit session: Session): Unit = {

    val stateIds = (1 until AnalysisJobStates.VALID_STATES.size).toList

    def randomState(sIds: List[Int]) = Random.shuffle(sIds).head

    def _toJob(n: Int) = EngineJob(n,
      UUID.randomUUID(),
      s"Job name $n",
      s"Comment for job $n",
      JodaDateTime.now(),
      JodaDateTime.now(),
      AnalysisJobStates.CREATED,
      "mock-pbsmrtpipe-job-type",
      "path",
      "{}",
      Some("root")
    )

    engineJobs ++= (1 until _MOCK_NJOBS).map(x => _toJob(x))

  }

  def insertMockSubreadDataSetsFromDir()(implicit session: Session): Unit = {

    val name = "datasets-subreads-rs-converted"
    val files = getMockDataSetFiles(name)

    def toS(file: File): SubreadServiceDataSet = {
      val userId = 1
      val projectId = 1
      val jobId = 1
      logger.info(s"Loading mock data from ${file.toPath.toAbsolutePath.toString}")
      val d = DataSetLoader.loadSubreadSet(file.toPath)
      logger.info(s"DataSet $d")
      val sds = Converters.convert(d, file.toPath.toAbsolutePath, _MOCK_USER_ID, _MOCK_JOB_ID, _MOCK_PROJECT_ID)
      logger.info(s"Loading dataset $sds")
      sds
    }
    val sds = files.map(x => toS(x))
    sds.foreach(x => dao.insertSubreadDataSet(x))
  }

  def insertMockHdfSubreadDataSetsFromDir()(implicit session: Session): Unit = {

    val name = "datasets-hdfsubreads-rs-converted"
    val files = getMockDataSetFiles(name)

    def toS(file: File): HdfSubreadServiceDataSet = {
      logger.info(s"Loading mock data from ${file.toPath.toAbsolutePath.toString}")
      val d = DataSetLoader.loadHdfSubreadSet(file.toPath)
      logger.info(s"DataSet $d")
      val sds = Converters.convert(d, file.toPath.toAbsolutePath, _MOCK_USER_ID, _MOCK_JOB_ID, _MOCK_PROJECT_ID)
      logger.info(s"Loading dataset $sds")
      sds
    }
    files.foreach(x => dao.insertHdfSubreadDataSet(toS(x)))
  }

  def insertMockReferenceDataSetsFromDir()(implicit session: Session): Unit = {

    val name = "datasets-references-rs-converted"
    val files = getMockDataSetFiles(name)

    def toS(file: File): ReferenceServiceDataSet = {
      val dataset = DataSetLoader.loadReferenceSet(file.toPath)
      logger.debug(s"Loading reference from ${file.toPath}")
      Converters.convert(dataset, file.toPath, _MOCK_USER_ID, _MOCK_JOB_ID, _MOCK_PROJECT_ID)
    }
    files.map(x => dao.insertReferenceDataSet(toS(x)))
  }

  def insertMockAlignmentDataSets()(implicit session: Session): Unit = {
    def _toDS(n: Int) = {
      val uuid = UUID.randomUUID()
      AlignmentServiceDataSet(n,
        UUID.randomUUID(),
        s"Alignment DataSet $n",
        s"/path/to/dataset/$n.xml",
        JodaDateTime.now(),
        JodaDateTime.now(),
        1,
        9876,
        _MOCK_DS_VERSION,
        "mock Alignment Dataset comments",
        "mock-alignment-dataset-tags", toMd5(uuid.toString), _MOCK_USER_ID, _MOCK_JOB_ID, _MOCK_PROJECT_ID)
    }
    val dss = (1 until _MOCK_NDATASETS).map(x => _toDS(x))
    dss.foreach(x => dao.insertAlignmentDataSet(x))
  }

  def insertMockJobStates()(implicit session: Session): Unit = {
    def toS(name: String) = (-99, name, s"State $name description", JodaDateTime.now(), JodaDateTime.now())
    jobStates ++= AnalysisJobStates.VALID_STATES.map(x => toS(x.toString))
  }

  def insertMockJobEvents()(implicit session: Session): Unit = {
    val stateIds = (1 until 4).toList
    val jobIds = (1 until 4).toList
    val maxEvents = (2 until 5).toList

    def randomElement(x: List[Int])(): Int = Random.shuffle(x).head

    def toE(i: Int) = JobEvent(UUID.randomUUID(), i, AnalysisJobStates.CREATED, s"message from job $i", JodaDateTime.now())
    def toEs(jobId: Int, nevents: Int) = (1 until randomElement(maxEvents)).toList.map(i => toE(jobId))

    jobEvents ++= jobIds.flatMap(x => toEs(x, randomElement(maxEvents)))
  }

  def insertMockJobsTags()(implicit session: Session): Unit = {

    def randomInt(x: List[Int]) = Random.shuffle(x).head

    val jobIds = (1 until _MOCK_NJOBS).toList
    val tags = Seq("filtering", "mapping", "ecoli", "lambda", "myProject") ++ (1 until 10).map(i => s"Tag $i")
    val tagIds = tags.indices.toList

    jobTags ++= tagIds.map(i => (i, tags(i)))

    jobsTags ++= jobIds.map( (_, randomInt(tagIds)) )
  }

  def insertMockProject()(implicit session: Session): Unit = {

    val projectId = 1
    projects += Project(projectId, "Project 1", "Project 1 description", "CREATED", JodaDateTime.now(), JodaDateTime.now())
    projectsUsers += ProjectUser(projectId, "mkocher", "OWNER")
  }

  def insertMockDataStoreFiles()(implicit session: Session): Unit = {
    val job = engineJobs.filter(_.id === _MOCK_JOB_ID).first
    datastoreServiceFiles += DataStoreServiceFile(
      UUID.randomUUID(),
      FileTypes.REPORT.fileTypeId.toString,
      "test",
      0,
      JodaDateTime.now(),
      JodaDateTime.now(),
      JodaDateTime.now(),
      "/fake",
      job.id,
      job.uuid,
      "fake name",
      "fake description"
    )
  }
}

trait TmpDirJobResolver {
  val tmpPath = FileUtils.getTempDirectory
  val resolver = new SimpleUUIDJobResolver(tmpPath.toPath)
}

trait InitializeTables extends MockUtils {

  val dal: Dal
  def createTables:Unit = {
    dao.dal.db.withSession { implicit session =>
      dao.initializeDb()
    }
  }

  /**
   * Required data in db
   */
  def loadBaseMock = {

    dao.dal.db.withSession {implicit session =>
      insertMockJobStates()
      insertMockProject()
    }
    logger.info("Completed loading base database resources (User, Project, DataSet Types, JobStates)")
  }

}
