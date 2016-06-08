package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent}
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, SimpleUUIDJobResolver}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.database.FlywayMigrator
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

trait SetupMockData extends MockUtils {
  def runSetup(dao: JobsDao): Unit =
    Await.ready(
      new FlywayMigrator(dao.dbURI).init() map { _ =>
      println(s"Created database connection from URI ${dao.dbURI}")} flatMap { _ =>
      insertMockProject()} flatMap { _ =>
      insertMockSubreadDataSetsFromDir()} flatMap { _ =>
      insertMockHdfSubreadDataSetsFromDir()} flatMap { _ =>
      insertMockReferenceDataSetsFromDir()} flatMap { _ =>
      insertMockAlignmentDataSets()} flatMap { _ =>

      // Jobs
      insertMockJobs()} flatMap { _ =>
      insertMockJobEvents()} flatMap { _ =>
      insertMockJobsTags()} flatMap { _ =>

      // datastore
      insertMockDataStoreFiles()} map { _ =>
      println("Completed inserting mock data.")}, 1.minute)
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
    def toJob(n: Int) = EngineJob(
        n,
        UUID.randomUUID(),
        s"Job name $n",
        s"Comment for job $n",
        JodaDateTime.now(),
        JodaDateTime.now(),
        AnalysisJobStates.CREATED,
        "mock-pbsmrtpipe",
        "path",
        "{}",
        Some("root")
    )
    dao.db.run(engineJobs ++= (1 until _MOCK_NJOBS).map(toJob))
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
    def toDS(n: Int) = {
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
    val dss = (1 until _MOCK_NDATASETS).map(toDS)
    Future.sequence(dss.map(dao.insertAlignmentDataSet))
  }

  def insertMockJobEvents(): Future[Option[Int]] = {
    val jobIds = (1 until 4).toList
    val maxEvents = (2 until 5).toList

    def randomElement(x: List[Int])(): Int = Random.shuffle(x).head

    def toE(i: Int) = JobEvent(UUID.randomUUID(), i, AnalysisJobStates.CREATED, s"message from job $i", JodaDateTime.now())
    def toEs(jobId: Int, nevents: Int) = (1 until randomElement(maxEvents)).toList.map(i => toE(jobId))

    dao.db.run(jobEvents ++= jobIds.flatMap(toEs(_, randomElement(maxEvents))))
  }

  def insertMockJobsTags(): Future[Unit] = {
    def randomInt(x: List[Int]) = Random.shuffle(x).head

    val jobIds = (1 until _MOCK_NJOBS).toList
    val tags = Seq("filtering", "mapping", "ecoli", "lambda", "myProject") ++ (1 until 10).map(i => s"Tag $i")
    val tagIds = tags.indices.toList

    dao.db.run(
      DBIO.seq(
        jobTags ++= tagIds.map(i => (i, tags(i))),
        jobsTags ++= jobIds.map( (_, randomInt(tagIds)) )
      )
    )
  }

  def insertMockProject(): Future[Unit] = {
    val projectId = 1
    dao.db.run(
      DBIO.seq(
        projects += Project(projectId, "Project 1", "Project 1 description", "CREATED", JodaDateTime.now(), JodaDateTime.now()),
        projectsUsers += ProjectUser(projectId, "mkocher", "OWNER")
      )
    )
  }

  def insertMockDataStoreFiles(): Future[Int] = {
    dao.db.run(
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
