package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  ConfigLoader,
  EngineCoreConfigLoader
}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  PacBioIntJobResolver
}
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime, Duration => JodaDuration}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.util.Random
import slick.jdbc.PostgresProfile.api._

trait SetupMockData extends MockUtils with InitializeTables {

  /**
    * Hacky Inserts Mock data for
    *
    * - Projects
    * - SubreadsSets
    * - HdfSubreadSets
    * - ReferenceSets
    * - AlignmentSets
    * - Mock Jobs (mock-pbsmrtpipe type)
    * - Job Events
    * - DataStore files
    *
    * This is motivated by Stress testing.
    *
    * @param dao Jobs Dao
    */
  def runInsertAllMockData(dao: JobsDao): Int = {

    // This must be done sequentially because of
    // foreign key constraints
    val fx = for {
      project <- insertMockTestProject()
      m1 <- insertExampleSubreadSetsFromResourcesDir(project.id)
      _ <- insertExampleBarcodeSetsFromResourcesDir(project.id)
      //_ <- insertMockHdfSubreadDataSetsFromDir()
      m2 <- insertMockReferenceDataSetsFromDir(project.id)
      //_ <- insertMockAlignmentDataSets()
      // Jobs
      _ <- insertMockJobs(projectId = project.id)
      _ <- insertMockDataStoreFiles()
    } yield
      (project.id,
       s"Successfully inserted DataSets from 'resources' and imported MockJobs\n$m1\n$m2")

    val (createdProjectId, results) = Await.result(fx, 3.minute)
    logger.info(s"Created Mock Project id:$createdProjectId name $results")
    createdProjectId
  }
}

/**
  * clumsy way to setup the test db
  */
trait MockUtils extends LazyLogging {

  import com.pacbio.secondary.smrtlink.database.TableModels._

  val dao: JobsDao

  // This is a weak way to indentify MOCK jobs from real jobs
  val MOCK_JOB_NAME_PREFIX = "MOCK-"

  val MOCK_DS_VERSION = "0.5.0"
  val MOCK_NJOBS = 5
  val MOCK_NDATASETS = 5
  val MOCK_NUM_PIPELINE_TEMPLATES = 5
  val ROOT_MOCK_DATASET_DIR = "/mock-datasets"
  val MOCK_CREATED_BY = Some("testuser")
  val MOCK_JOB_ID = 1
  val MOCK_USER_LOGIN = "jsnow"
  val GEN_PROJECT_ID = 1

  // All Mock/Test Files are added to this project
  val TEST_PROJECT_NAME = "TEST_PROJECT"
  val TEST_PROJECT_REQUEST = ProjectRequest(
    TEST_PROJECT_NAME,
    "Mock Project description",
    state = Some(ProjectState.CREATED),
    None,
    None,
    Some(Seq(ProjectRequestUser(MOCK_USER_LOGIN, ProjectUserRole.OWNER)))
  )

  def toMd5(text: String): String =
    MessageDigest
      .getInstance("MD5")
      .digest(text.getBytes)
      .map("%02x".format(_))
      .mkString

  def getMockDataSetFiles(dirName: String): Seq[File] = {
    val u = getClass.getResource(ROOT_MOCK_DATASET_DIR)
    val p = Paths.get(u.toURI)
    val f = p.resolve(dirName).toFile
    val files = f.listFiles.toList
    // println(s"Loading mock data from $f. Found ${files.length} mock files.")
    files
  }

  def insertMockJobs(numJobs: Int = MOCK_NJOBS,
                     jobType: String = "mock-pbsmrtpipe",
                     nchunks: Int = 100,
                     projectId: Int): Future[Iterator[Option[Int]]] = {

    val states = AnalysisJobStates.VALID_STATES
    val rnd = new Random

    def getRandomState = states.toVector(rnd.nextInt(states.size))

    def toJob = {

      val now = JodaDateTime.now()
      val uuid = UUID.randomUUID()
      EngineJob(
        -1,
        uuid,
        s"$MOCK_JOB_NAME_PREFIX Job name $uuid",
        s"Comment for job $uuid",
        now,
        now,
        now,
        getRandomState,
        jobType,
        "path",
        "{}",
        Some("root"),
        None,
        None,
        projectId = projectId
      )
    }
    val jobChunks = (0 until numJobs).grouped(scala.math.min(nchunks, numJobs))
    Future.sequence(jobChunks.map(jobIds =>
      dao.db.run(engineJobs ++= jobIds.map(x => toJob))))
  }

  def insertExampleBarcodeSetsFromResourcesDir(
      projectId: Int): Future[Seq[MessageResponse]] = {
    val name = "datasets-barcodesets"
    val files = getMockDataSetFiles(name)
    val now = JodaDateTime.now()

    def toS(file: File): DataStoreFile = {
      logger.info(
        s"Loading mock data from ${file.toPath.toAbsolutePath.toString}")
      val d = DataSetLoader.loadBarcodeSet(file.toPath)
      logger.info(s"DataSet $d")
      val sds = Converters.convertBarcodeSet(d,
                                             file.toPath.toAbsolutePath,
                                             MOCK_CREATED_BY,
                                             MOCK_JOB_ID,
                                             projectId)
      logger.info(s"Loading dataset $sds")
      sds.toDataStoreFile("source-id", FileTypes.DS_BARCODE, file.length())
    }

    // This is a bit goofy.
    val jobId = 1
    val engineJob = EngineJob(
      jobId,
      UUID.randomUUID(),
      "Mock Job",
      "Mock Job Comment",
      now,
      now,
      now,
      AnalysisJobStates.SUCCESSFUL,
      JobTypeIds.IMPORT_DATASET.toString,
      "",
      "{}",
      None,
      None,
      None
    )

    val getJobOrInsert: Future[EngineJob] = dao
      .getJobById(jobId)
      .recoverWith {
        case _ =>
          dao.importRawEngineJob(engineJob)
      }

    for {
      job <- getJobOrInsert
      files <- Future.successful(files.map(f => toS(f)))
      results <- Future.sequence(
        files.map(f => dao.importDataStoreFile(f, job.uuid)))
    } yield results
  }

  def insertExampleSubreadSetsFromResourcesDir(
      projectId: Int): Future[Seq[MessageResponse]] = {
    val name = "datasets-subreads-rs-converted"
    val files = getMockDataSetFiles(name)
    val now = JodaDateTime.now()

    def toS(file: File): DataStoreFile = {
      logger.info(
        s"Loading mock data from ${file.toPath.toAbsolutePath.toString}")
      val d = DataSetLoader.loadSubreadSet(file.toPath)
      logger.info(s"DataSet $d")
      val sds = Converters.convertSubreadSet(d,
                                             file.toPath.toAbsolutePath,
                                             MOCK_CREATED_BY,
                                             MOCK_JOB_ID,
                                             projectId)
      logger.info(s"Loading dataset $sds")
      sds.toDataStoreFile("source-id", FileTypes.DS_SUBREADS, file.length())
    }

    // This is a bit goofy.
    val jobId = 10
    val engineJob = EngineJob(
      jobId,
      UUID.randomUUID(),
      "Mock Job",
      "Mock Job Comment",
      now,
      now,
      now,
      AnalysisJobStates.SUCCESSFUL,
      JobTypeIds.IMPORT_DATASET.toString,
      "",
      "{}",
      None,
      None,
      None
    )

    val getJobOrInsert: Future[EngineJob] = dao
      .getJobById(jobId)
      .recoverWith {
        case e => dao.importRawEngineJob(engineJob)
      }

    for {
      job <- getJobOrInsert
      files <- Future.successful(files.map(f => toS(f)))
      results <- Future.sequence(
        files.map(f => dao.importDataStoreFile(f, job.uuid)))
    } yield results
  }

//  def insertMockHdfSubreadDataSetsFromDir(): Future[Seq[MessageResponse]] = {
//    val name = "datasets-hdfsubreads-rs-converted"
//    val files = getMockDataSetFiles(name)
//
//    def toS(file: File): HdfSubreadServiceDataSet = {
//      logger.info(
//        s"Loading mock data from ${file.toPath.toAbsolutePath.toString}")
//      val d = DataSetLoader.loadHdfSubreadSet(file.toPath)
//      logger.info(s"DataSet $d")
//      val sds = Converters.convertHdfSubreadSet(d,
//                                                file.toPath.toAbsolutePath,
//                                                MOCK_CREATED_BY,
//                                                MOCK_JOB_ID,
//                                                mockProjectId)
//      logger.info(s"Loading dataset $sds")
//      sds
//    }
//    Future.sequence(files.map(toS).map(dao.insertHdfSubreadDataSet))
//  }

  def insertMockReferenceDataSetsFromDir(
      projectId: Int): Future[Seq[MessageResponse]] = {
    val name = "datasets-references-rs-converted"
    val files = getMockDataSetFiles(name)
    val now = JodaDateTime.now()

    def toS(file: File): DataStoreFile = {
      val dataset = DataSetLoader.loadReferenceSet(file.toPath)
      logger.debug(s"Loading reference from ${file.toPath}")
      val sds = Converters.convertReferenceSet(dataset,
                                               file.toPath,
                                               MOCK_CREATED_BY,
                                               MOCK_JOB_ID,
                                               projectId)

      sds.toDataStoreFile("source-id", FileTypes.DS_REFERENCE, file.length())
    }

    // This is a bit goofy.
    val jobId = 1
    val engineJob = EngineJob(
      jobId,
      UUID.randomUUID(),
      "Mock Job",
      "Mock Job Comment",
      now,
      now,
      now,
      AnalysisJobStates.SUCCESSFUL,
      JobTypeIds.IMPORT_DATASET.toString,
      "",
      "{}",
      None,
      None,
      None
    )

    val getJobOrInsert: Future[EngineJob] = dao
      .getJobById(jobId)
      .recoverWith {
        case e => dao.importRawEngineJob(engineJob)
      }

    for {
      job <- getJobOrInsert
      files <- Future.successful(files.map(f => toS(f)))
      results <- Future.sequence(
        files.map(f => dao.importDataStoreFile(f, job.uuid)))
    } yield results
  }

//  def insertMockAlignmentDataSets(
//      n: Int = MOCK_NDATASETS): Future[Seq[MessageResponse]] = {
//    def toDS = {
//      val uuid = UUID.randomUUID()
//      AlignmentServiceDataSet(
//        -1,
//        UUID.randomUUID(),
//        s"Alignment DataSet $uuid",
//        s"/path/to/dataset/$uuid.xml",
//        JodaDateTime.now(),
//        JodaDateTime.now(),
//        1,
//        9876,
//        MOCK_DS_VERSION,
//        "mock Alignment Dataset comments",
//        "mock-alignment-dataset-tags",
//        toMd5(uuid.toString),
//        MOCK_CREATED_BY,
//        MOCK_JOB_ID,
//        mockProjectId
//      )
//    }
//    val dss = (0 until n).map(x => toDS)
//    Future.sequence(dss.map(dao.insertAlignmentDataSet))
//  }

  def insertMockTestProject(): Future[Project] =
    dao.createProject(TEST_PROJECT_REQUEST)

  def insertMockProject(): Future[Int] = {
    val f = dao.db.run(
      for {
        pid <- (projects returning projects.map(_.id)) += Project(
          -1,
          TEST_PROJECT_NAME,
          "Mock Project description",
          ProjectState.CREATED,
          JodaDateTime.now(),
          JodaDateTime.now(),
          isActive = true,
          grantRoleToAll = None)
        _ <- projectsUsers += ProjectUser(pid,
                                          MOCK_USER_LOGIN,
                                          ProjectUserRole.OWNER)
      } yield pid
    )
    f
  }

  def toMockDataStoreFile(jobId: Int,
                          jobUUID: UUID,
                          name: String = "mock-file") = DataStoreServiceFile(
    UUID.randomUUID(),
    FileTypes.REPORT.fileTypeId.toString,
    name,
    0,
    JodaDateTime.now(),
    JodaDateTime.now(),
    JodaDateTime.now(),
    "/fake",
    jobId,
    jobUUID,
    "fake name",
    "fake description"
  )

  def insertMockDataStoreFilesForMockJobs(numFiles: Int = 10,
                                          nchunks: Int = 100): Future[Unit] = {

    def toDataStoreFile(job: EngineJob): Seq[DataStoreServiceFile] = {
      (0 until numFiles).map(i =>
        toMockDataStoreFile(job.id, job.uuid, s"mock-$i"))
    }

    val fx = for {
      engineJobs <- dao.getEngineCoreJobs()
      files <- Future.successful(engineJobs.flatMap(toDataStoreFile))
      batchedFiles <- Future {
        files.grouped(scala.math.min(nchunks, files.length))
      }
      _ <- Future.sequence(
        batchedFiles.map(xs => dao.db.run(datastoreServiceFiles ++= xs)))
    } yield ()

    fx
  }

  def insertMockDataStoreFiles(): Future[Int] = {
    dao.db.run(
      engineJobs.filter(_.id === MOCK_JOB_ID).result.head.flatMap { job =>
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
  val resolver = new PacBioIntJobResolver(tmpPath.toPath)
}

trait InitializeTables extends MockUtils {
  val db: Database

  def createTables: Unit = {
    logger.info("Applying migrations")
    //db.migrate()
    logger.info("Completed applying migrations")
  }
}
