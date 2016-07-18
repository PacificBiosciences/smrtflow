package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID

import com.pacbio.database.Database
import com.pacbio.secondary.analysis.configloaders.EngineCoreConfigLoader
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, SimpleUUIDJobResolver}
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{Duration => JodaDuration, DateTime => JodaDateTime}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.util.Random
import slick.driver.H2Driver.api._

trait SetupMockData extends MockUtils with InitializeTables {
  def runSetup(dao: JobsDao): Unit = {

    createTables()
    println(s"Created database connection from URI ${dao.db.dbUri}")

    val f = Future(println("Inserting mock data")).flatMap { _ =>
      Future.sequence(Seq(
        insertMockProject(),
        insertMockSubreadDataSetsFromDir(),
        insertMockHdfSubreadDataSetsFromDir(),
        insertMockReferenceDataSetsFromDir(),
        insertMockAlignmentDataSets(),

        // Jobs
        insertMockJobs(),
        insertMockJobEvents(),

        // datastore
        insertMockDataStoreFiles()
      ))
    }.andThen { case _ => println("Completed inserting mock data.") }

    Await.result(f, 2.minute)
  }
}

/**
 * clumsy way to setup the test db
 */
trait   MockUtils extends LazyLogging{

  import com.pacbio.secondary.smrtlink.database.TableModels._

  val dao: JobsDao

  // This is a weak way to indentify MOCK jobs from real jobs
  val MOCK_JOB_NAME_PREFIX = "MOCK-"

  val MOCK_DS_VERSION = "0.5.0"
  val MOCK_NJOBS = 5
  val MOCK_NDATASETS = 5
  val MOCK_NUM_PIPELINE_TEMPLATES = 5
  val ROOT_MOCK_DATASET_DIR = "/mock-datasets"
  val MOCK_USER_ID = 1
  val MOCK_PROJECT_ID = 1
  val MOCK_JOB_ID = 1

  def toMd5(text: String): String = MessageDigest.getInstance("MD5").digest(text.getBytes).map("%02x".format(_)).mkString

  def getMockDataSetFiles(dirName: String): Seq[File] = {
    val u = getClass.getResource(ROOT_MOCK_DATASET_DIR)
    val p = Paths.get(u.toURI)
    val f = p.resolve(dirName).toFile
    val files = f.listFiles.toList
    println(s"Loading mock data from $f. Found ${files.length} mock files.")
    files
  }

  def insertMockJobs(numJobs: Int = MOCK_NJOBS, jobType: String = "mock-pbsmrtpipe", nchunks: Int = 100): Future[Iterator[Option[Int]]] = {

    val rnd = new Random
    def random[T](s: Seq[T]): T = s(rnd.nextInt(s.size))

    // TODO(smcclellan): Add tags to mock jobs?
    val tags = Seq("filtering", "mapping", "ecoli", "lambda", "myProject") ++ (1 until 10).map(i => s"Tag $i")

    def toJob = {
      val uuid = UUID.randomUUID()
      EngineJob(
        -1,
        uuid,
        s"$MOCK_JOB_NAME_PREFIX Job name $uuid",
        s"Comment for job $uuid",
        JodaDateTime.now(),
        JodaDateTime.now(),
        random(AnalysisJobStates.VALID_STATES),
        jobType,
        "path",
        "{}",
        Some("root"))}
    val jobChunks = (0 until numJobs).grouped(scala.math.min(nchunks, numJobs))
    Future.sequence(jobChunks.map(jobIds => dao.db.run(engineJobs ++= jobIds.map(x => toJob))))
  }

  def insertDummySubreadSets(n: Int): Future[Seq[String]] = {
    def toS = {
      val importJobId = 1
      val ux = UUID.randomUUID()
      val now = JodaDateTime.now()
      SubreadServiceDataSet(-1, ux, "DataSet",
        "/path/to/dataset.xml",
        now,
        now,
        1,
        1L,
        "3.1.0",
        "Comment",
        "tag1, tag2",
        "md5",
        "inst-name",
        "movie-context-id",
        "well-sample-name",
        "well-anme",
        "bio-sample",
        0,
        "run-name",
        MOCK_USER_ID, importJobId, MOCK_PROJECT_ID)
    }

    Future.sequence((0 until n).map(_ => dao.insertSubreadDataSet(toS)))
  }

  def insertMockSubreadDataSetsFromDir(): Future[Seq[String]] = {
    val name = "datasets-subreads-rs-converted"
    val files = getMockDataSetFiles(name)

    def toS(file: File): SubreadServiceDataSet = {
      logger.info(s"Loading mock data from ${file.toPath.toAbsolutePath.toString}")
      val d = DataSetLoader.loadSubreadSet(file.toPath)
      logger.info(s"DataSet $d")
      val sds = Converters.convert(d, file.toPath.toAbsolutePath, MOCK_USER_ID, MOCK_JOB_ID, MOCK_PROJECT_ID)
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
      val sds = Converters.convert(d, file.toPath.toAbsolutePath, MOCK_USER_ID, MOCK_JOB_ID, MOCK_PROJECT_ID)
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
      Converters.convert(dataset, file.toPath, MOCK_USER_ID, MOCK_JOB_ID, MOCK_PROJECT_ID)
    }
    Future.sequence(files.map(toS).map(dao.insertReferenceDataSet))
  }

  def insertMockAlignmentDataSets(n: Int = MOCK_NDATASETS): Future[Seq[String]] = {
    def toDS =  {
      val uuid = UUID.randomUUID()
      AlignmentServiceDataSet(-1,
        UUID.randomUUID(),
        s"Alignment DataSet $uuid",
        s"/path/to/dataset/$uuid.xml",
        JodaDateTime.now(),
        JodaDateTime.now(),
        1,
        9876,
        MOCK_DS_VERSION,
        "mock Alignment Dataset comments",
        "mock-alignment-dataset-tags", toMd5(uuid.toString), MOCK_USER_ID, MOCK_JOB_ID, MOCK_PROJECT_ID)
    }
    val dss = (0 until n).map(x => toDS)
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

  def insertMockJobDatasets(nchunks: Int = 100, datasetsPerJob: Double = 0.7): Future[Unit] = {
    for {
      jobs <- dao.db.run(engineJobs.result)
      //not perfectly realistic; just using subreads because
      //dataset_metadata doesn't have a dataset type column
      //and it's a bit awkward to get the types by joining
      subreadSets <- dao.db.run(dsSubread2.result)
      nRows = (jobs.length * datasetsPerJob).toInt
      randomJobs = Stream.continually(Random.shuffle(jobs)).flatten.take(nRows)
      randomDatasets = Stream.continually(Random.shuffle(subreadSets)).flatten.take(nRows)
      jobDatasets = randomJobs.zip(randomDatasets)
      entryPoints = jobDatasets.map(x => EngineJobEntryPoint(x._1.id, x._2.uuid, DataSetMetaTypes.Subread.toString))
      batches = entryPoints.grouped(scala.math.min(nchunks, entryPoints.length))
      _ <- Future.sequence(batches.map(batch => dao.db.run(engineJobsDataSets ++= batch)))
    } yield ()
  }

  def insertMockJobsTags(): Future[Unit] = {
    def randomInt(x: List[Int]) = Random.shuffle(x).head

    val jobIds = (1 until MOCK_NJOBS).toList
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
    val project =
      Project(-1, "Project 1", "Project 1 description", "CREATED", JodaDateTime.now(), JodaDateTime.now())
    for {
      id <- dao.db.run(projects returning projects.map(_.id) += project)
      _  <- dao.db.run(projectsUsers += ProjectUser(id, "mkocher", "OWNER"))
    } yield ()
  }

  /**
    * Insert a minimal set of Job States for each Job
    *
    *
    * @param nchunks Number of batches to import
    * @return
    */
  def insertMockJobEventsForMockJobs(nchunks: Int = 100): Future[Unit] = {

    def toJobEvents(jobId: Int): Seq[JobEvent] =
      Seq(AnalysisJobStates.CREATED, AnalysisJobStates.RUNNING, AnalysisJobStates.SUCCESSFUL)
          .map(s => JobEvent(UUID.randomUUID, jobId, s, "Update status", JodaDateTime.now()))

    val fx = for {
      engineJobs <- dao.getJobs()
      jobIds <- Future { engineJobs.filter(_.name.startsWith(MOCK_JOB_NAME_PREFIX)).map(_.id)}
      events <- Future {jobIds.map(toJobEvents).flatMap(identity)}
      batchedEvents <- Future {events.grouped(scala.math.min(nchunks, events.length))}
      _ <- Future.sequence(batchedEvents.map(events => dao.addJobEvents(events)))
    } yield ()

    fx
  }
  def toMockDataStoreFile(jobId: Int, jobUUID: UUID, name: String = "mock-file") = DataStoreServiceFile(
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

  def insertMockDataStoreFilesForMockJobs(numFiles: Int = 10, nchunks: Int = 100): Future[Unit] = {

    def toDataStoreFile(job: EngineJob): Seq[DataStoreServiceFile] = {
      (0 until numFiles).map(i => toMockDataStoreFile(job.id, job.uuid, s"mock-$i"))
    }

    val fx = for {
      engineJobs <- dao.getJobs()
      files <- Future { engineJobs.map(toDataStoreFile).flatMap(identity)}
      batchedFiles <- Future { files.grouped(scala.math.min(nchunks, files.length)) }
      _ <- Future.sequence(batchedFiles.map(xs => dao.db.run(datastoreServiceFiles ++= xs)))
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
  val resolver = new SimpleUUIDJobResolver(tmpPath.toPath)
}

trait InitializeTables extends MockUtils {
  val db: Database

  def createTables(): Unit = {
    logger.info("Applying migrations")
    db.migrate()
    logger.info("Completed applying migrations")
  }

  /**
   * Required data in db
   */
  def loadBaseMock() = {
    Await.result(insertMockProject(), 10.seconds)
    logger.info("Completed loading base database resources (User, Project, DataSet Types, JobStates)")
  }
}


object InsertMockData extends App
    with TmpDirJobResolver
    with InitializeTables
    with EngineCoreConfigLoader
    with SetupMockData{

  // Max number of jobs to query
  val maxJobs = 20000

  // Number of chunks to batch up commits for events and datastore files
  val numChunks = conf.getInt("mock.nchunks")

  // Jobs
  val maxPbsmrtpipeJobs = conf.getInt("mock.pbsmrtpipe-jobs")
  val maxImportDataSetJobs = conf.getInt("mock.import-dataset-jobs")

  // DataSets
  val numSubreadSets = conf.getInt("mock.subreadsets")
  val numAlignmentSets = conf.getInt("mock.alignmentsets")
  val numReferenceSets = conf.getInt("mock.referencesets")

  def toURI(sx: String) = if (sx.startsWith("jdbc:h2:")) sx else throw new Exception(s"Bad JDBC URL? $sx")

  val db = new Database(toURI(conf.getString("pb-services.db-uri")))
  val dao = new JobsDao(db, engineConfig, resolver)

  def runner(args: Array[String]): Int = {
    println(s"Loading DB ${dao.db.dbUri}")

    val startedAt = JodaDateTime.now()

    createTables()

    println(s"Jobs     to import -> pbsmrtpipe:$maxPbsmrtpipeJobs import-dataset:$maxImportDataSetJobs")
    println(s"DataSets to import -> SubreadSets:$numSubreadSets alignmentsets:$numAlignmentSets")

    val fsummary = dao.getSystemSummary("Initial System Summary")

    fsummary onSuccess { case summary => println(summary) }

    val fx = for {
      _ <- fsummary
      _ <- insertMockProject()
      _ <- insertDummySubreadSets(numSubreadSets)
      _ <- insertMockAlignmentDataSets(numAlignmentSets)
      _ <- insertMockJobs(maxPbsmrtpipeJobs, "pbsmrtpipe", numChunks)
      _ <- insertMockJobs(maxImportDataSetJobs, "import-dataset", numChunks)
      _ <- insertMockJobEventsForMockJobs(numChunks)
      _ <- insertMockDataStoreFilesForMockJobs(5, numChunks)
      _ <- insertMockJobDatasets(numChunks)
      sx <- dao.getSystemSummary("Final System Summary")
    } yield sx


    val result = Await.result(fx, Duration.Inf)
    println(result)

    val exitCode = 0
    val runtime = new JodaDuration(startedAt, JodaDateTime.now())

    println(s"Exiting main with exit code $exitCode in ${runtime.getStandardSeconds} seconds")
    // Not sure why this is necessary, but it is. Otherwise it will hang
    System.exit(exitCode)
    exitCode
  }

  runner(args)

}
