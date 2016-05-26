package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.jobs.JobModels.{JobEvent, EngineJob}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.analysis.jobs.{SimpleUUIDJobResolver, AnalysisJobStates}
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils

import org.joda.time.{DateTime => JodaDateTime}

import slick.driver.SQLiteDriver.api._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random


trait SetupMockData extends MockUtils with InitializeTables {
  def runSetup(dao: JobsDao): Future[Seq[Any]] = {
    println(s"Created database connection from URI ${dao.dal.dbURI}")

    createTables
    loadBaseMock

    Future(println("Inserting mock data")).flatMap { _ =>
      Future.sequence(Seq(
        insertMockSubreadDataSetsFromDir(),
        insertMockHdfSubreadDataSetsFromDir(),
        insertMockReferenceDataSetsFromDir(),
        insertMockAlignmentDataSets(),

        // Jobs
        insertMockJobs(),
        insertMockJobEvents(),
        insertMockJobsTags(),

        // datastore
        insertMockDataStoreFiles()
      ))
    }.andThen { case _ => println("Completed inserting mock data.") }
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

  def insertMockJobs(): Future[Option[Int]] = {
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
    dao.dal.db.run(engineJobs ++= (1 until _MOCK_NJOBS).map(_toJob))
  }

  def insertMockSubreadDataSetsFromDir(): Future[Seq[String]] = {
    val name = "datasets-subreads-rs-converted"
    val files = getMockDataSetFiles(name)

    def toS(file: File): SubreadServiceDataSet = {
      logger.info(s"Loading mock data from ${file.toPath.toAbsolutePath.toString}")
      val d = DataSetLoader.loadSubreadSet(file.toPath)
      logger.info(s"DataSet $d")
      val sds = Converters.convert(d, file.toPath.toAbsolutePath, _MOCK_USER_ID, _MOCK_JOB_ID, _MOCK_PROJECT_ID)
      logger.info(s"Loading dataset $sds")
      sds
    }
    Future.sequence(files.map(toS).map(dao.insertSubreadDataSet))
  }

  def insertMockHdfSubreadDataSetsFromDir(): Future[Seq[String]] = {
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
    Future.sequence(files.map(toS).map(dao.insertHdfSubreadDataSet))
  }

  def insertMockReferenceDataSetsFromDir(): Future[Seq[String]] = {
    val name = "datasets-references-rs-converted"
    val files = getMockDataSetFiles(name)

    def toS(file: File): ReferenceServiceDataSet = {
      val dataset = DataSetLoader.loadReferenceSet(file.toPath)
      logger.debug(s"Loading reference from ${file.toPath}")
      Converters.convert(dataset, file.toPath, _MOCK_USER_ID, _MOCK_JOB_ID, _MOCK_PROJECT_ID)
    }
    Future.sequence(files.map(toS).map(dao.insertReferenceDataSet))
  }

  def insertMockAlignmentDataSets(): Future[Seq[String]] = {
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
    val dss = (1 until _MOCK_NDATASETS).map(_toDS)
    Future.sequence(dss.map(dao.insertAlignmentDataSet))
  }

  def insertMockJobEvents(): Future[Option[Int]] = {
    val jobIds = (1 until 4).toList
    val maxEvents = (2 until 5).toList

    def randomElement(x: List[Int])(): Int = Random.shuffle(x).head

    def toE(i: Int) = JobEvent(UUID.randomUUID(), i, AnalysisJobStates.CREATED, s"message from job $i", JodaDateTime.now())
    def toEs(jobId: Int, nevents: Int) = (1 until randomElement(maxEvents)).toList.map(i => toE(jobId))

    dao.dal.db.run(jobEvents ++= jobIds.flatMap(toEs(_, randomElement(maxEvents))))
  }

  def insertMockJobsTags(): Future[Unit] = {
    def randomInt(x: List[Int]) = Random.shuffle(x).head

    val jobIds = (1 until _MOCK_NJOBS).toList
    val tags = Seq("filtering", "mapping", "ecoli", "lambda", "myProject") ++ (1 until 10).map(i => s"Tag $i")
    val tagIds = tags.indices.toList

    dao.dal.db.run(
      DBIO.seq(
        jobTags ++= tagIds.map(i => (i, tags(i))),
        jobsTags ++= jobIds.map( (_, randomInt(tagIds)) )
      )
    )
  }

  def insertMockProject(): Future[Unit] = {
    val projectId = 1
    dao.dal.db.run(
      DBIO.seq(
        projects += Project(projectId, "Project 1", "Project 1 description", "CREATED", JodaDateTime.now(), JodaDateTime.now()),
        projectsUsers += ProjectUser(projectId, "mkocher", "OWNER")
      )
    )
  }

  def insertMockDataStoreFiles(): Future[Int] = {
    dao.dal.db.run(
      engineJobs.filter(_.id === _MOCK_JOB_ID).result.head.flatMap { job =>
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
    )
  }
}

trait TmpDirJobResolver {
  val tmpPath = FileUtils.getTempDirectory
  val resolver = new SimpleUUIDJobResolver(tmpPath.toPath)
}

trait InitializeTables extends MockUtils {
  val dal: Dal

  def createTables: Unit = dao.initializeDb()

  /**
   * Required data in db
   */
  def loadBaseMock = {
    Await.ready(insertMockProject(), 1.minute)
    logger.info("Completed loading base database resources (User, Project, DataSet Types, JobStates)")
  }
}
