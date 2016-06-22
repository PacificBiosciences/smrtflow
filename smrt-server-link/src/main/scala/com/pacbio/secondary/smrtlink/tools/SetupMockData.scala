package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID

import com.pacbio.database.Database
import com.pacbio.secondary.analysis.configloaders.EngineCoreConfigLoader
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, SimpleUUIDJobResolver}
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.util.Random
import slick.driver.SQLiteDriver.api._

trait SetupMockData extends MockUtils with InitializeTables {
  def runSetup(dao: JobsDao): Unit = {

    createTables
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
        insertMockJobsTags(),

        // datastore
        insertMockDataStoreFiles()
      ))
    }.andThen { case _ => println("Completed inserting mock data.") }

    Await.result(f, 1.minute)
  }
}

/**
 * clumsy way to setup the test db
 */
trait MockUtils extends LazyLogging{

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

  def insertMockJobs(numJobs: Int = MOCK_NJOBS, jobType: String = "mock-pbsmrtpipe"): Future[Option[Int]] = {

    val states = AnalysisJobStates.VALID_STATES
    val rnd = new Random

    def getRandomState = states.toVector(rnd.nextInt(states.size))

    def toJob = {

      val uuid = UUID.randomUUID()
      EngineJob(
        -1,
        uuid,
        s"$MOCK_JOB_NAME_PREFIX Job name $uuid",
        s"Comment for job $uuid",
        JodaDateTime.now(),
        JodaDateTime.now(),
        getRandomState,
        jobType,
        "path",
        "{}",
        Some("root"))}
    dao.db.run(engineJobs ++= (0 until numJobs ).map(x => toJob))
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
    val projectId = 1
    dao.db.run(
      DBIO.seq(
        projects += Project(projectId, "Project 1", "Project 1 description", "CREATED", JodaDateTime.now(), JodaDateTime.now()),
        projectsUsers += ProjectUser(projectId, "mkocher", "OWNER")
      )
    )
  }

  // Add Mock Events to all Mock Jobs
  def insertMockJobEventsForMockJobs: Future[Unit] = {

    val createdAt = JodaDateTime.now()

    def toJobEvents(jobId: Int): Seq[JobEvent] =
      Seq(AnalysisJobStates.CREATED, AnalysisJobStates.RUNNING, AnalysisJobStates.SUCCESSFUL)
          .map(s => JobEvent(UUID.randomUUID, jobId, s, "Update status", JodaDateTime.now()))

    // Batch up the inserts so it's not absurdly slow
    val nchunks = 20

    val fx = for {
      engineJobs <- dao.getJobs()
      jobIds <- Future { engineJobs.filter(_.name.startsWith(MOCK_JOB_NAME_PREFIX)).map(_.id)}
      events <- Future {jobIds.map(toJobEvents).flatMap(identity)}
      batchedEvents <- Future {events.grouped(scala.math.min(nchunks, events.length))}
      _ <- Future.sequence(batchedEvents.map(events => dao.addJobEvents(events)))
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

  /**
    * Simple Summary of Job list
 *
    * @param engineJobs List of Engine Jobs
    * @return Summary
    */
  def jobSummary(engineJobs: Seq[EngineJob]): String = {

    val states = engineJobs.map(_.state).toSet

    val summary = states.map(sx => s" $sx => ${engineJobs.count(_.state == sx)}")
        .reduceOption(_ + "\n" + _)
        .getOrElse("")

    Seq(s"Summary ${engineJobs.size} Jobs", summary).reduce(_ + "\n" + _)
  }

  def getSystemSummary(maxJobs: Int = 20000): Future[String] = {

    for {
      ssets <- dao.getSubreadDataSets()
      rsets <- dao.getReferenceDataSets()
      asets <- dao.getAlignmentDataSets(maxJobs)
      jobs <- dao.getJobs(maxJobs)
      jobEvents <- dao.getJobEvents
      ijobs <- Future { jobs.filter(_.jobTypeId == "import-dataset")}
      ajobs <- Future { jobs.filter(_.jobTypeId == "pbsmrtpipe") }
    } yield s" nsubreads:${ssets.length}\n references:${rsets.length}\n alignmentsets: ${asets.length} \nTotal Jobs:${jobSummary(jobs)} \nTotal JobEvents:${jobEvents.length} \nImportDataset:${jobSummary(ijobs)} \nAnalysisJobs ${jobSummary(ajobs)}"

  }


}

trait TmpDirJobResolver {
  val tmpPath = FileUtils.getTempDirectory
  val resolver = new SimpleUUIDJobResolver(tmpPath.toPath)
}

trait InitializeTables extends MockUtils {
  val db: Database

  def createTables: Unit = {
    logger.info("Applying migrations")
    db.migrate()
    logger.info("Completed applying migrations")
  }

  /**
   * Required data in db
   */
  def loadBaseMock = {
    Await.result(insertMockProject(), 10.seconds)
    logger.info("Completed loading base database resources (User, Project, DataSet Types, JobStates)")
  }
}


object InsertMockData extends App with TmpDirJobResolver with InitializeTables with EngineCoreConfigLoader with SetupMockData{

  val maxJobs = 20000

  val insertMaxAnalysisJobs = 5000
  val insertMaxInsertJobs = 10000
  val numSubreadSets = 10000
  val numAlignmentSets = 10000

  def toURI(sx: String) = if (sx.startsWith("jdbc:sqlite:")) sx else s"jdbc:sqlite:$sx"

  def runner(args: Array[String]): Int = {

    val db = new Database(toURI(conf.getString("pb-services.db-uri")))
    val dao = new JobsDao(db, engineConfig, resolver)

    println(s"Loading DB ${dao.db.dbUri}")

    createTables

    val initialSummary = Await.result(getSystemSummary(maxJobs), 5.seconds)
    println(s"Initial System Summary\n$initialSummary")

    val fx = for {
      _ <- insertMockProject()
      _ <- insertDummySubreadSets(numSubreadSets)
      _ <- insertMockAlignmentDataSets(numAlignmentSets)
      _ <- insertMockJobs(insertMaxAnalysisJobs, "pbsmrtpipe")
      _ <- insertMockJobs(insertMaxInsertJobs, "import-dataset")
      _ <- insertMockJobEventsForMockJobs
      sx <- getSystemSummary(maxJobs)
    } yield sx


    val result = Await.result(fx, 480.seconds)
    println(s"System Summary \n$result")

    println("Exiting main")
    // Not sure why this is necessary, but it is. Otherwise it will hang
    System.exit(0)
    0
  }

  runner(args)

}
