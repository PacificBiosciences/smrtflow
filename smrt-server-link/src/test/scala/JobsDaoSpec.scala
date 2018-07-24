import java.nio.file.Files
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import org.joda.time.{DateTime => JodaDateTime}
import org.specs2.mutable.Specification
import slick.jdbc.PostgresProfile.api._
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.{JobsDao, SmrtLinkTestDalProvider}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetLoader,
  DataSetWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  DataStoreFile,
  EngineJob,
  JobConstants,
  JobTypeIds
}
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  PacBioIntJobResolver
}
import com.pacbio.secondary.smrtlink.jobtypes.MultiAnalysisJobOptions
import com.pacbio.secondary.smrtlink.jsonprotocols.ServiceJobTypeJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestUtils}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import spray.json._

class JobsDaoSpec extends Specification with TestUtils with SetupMockData {
  sequential

  object TestProviders extends SmrtLinkTestDalProvider {}

  val tmpJobDir = Files.createTempDirectory("jobs-dao-spec")
  val jobResolver = new PacBioIntJobResolver(tmpJobDir)

  val dao =
    new JobsDao(TestProviders.dbConfig.toDatabase, jobResolver)

  val db: Database = dao.db
  val timeout = FiniteDuration(10, "seconds")

  step(setupDb(TestProviders.dbConfig))
  step(runInsertAllMockData(dao))

  def validateSize(f: => Future[Int], x: Int) = {
    Await.result(f, timeout) === x
  }

  "Sanity Test" should {
    "Can connect to database" in {
      true === true
    }
    "Sanity DAO insertion test" in {
      validateSize(dao.getDataStoreFiles().map(_.length), 8)
    }
    "Get DataSetMeta" in {
      validateSize(dao.getDataSetMetas(activity = None).map(_.length), 7)
    }
    "Get SubreadSets" in {
      validateSize(
        dao.getSubreadDataSets(DataSetSearchCriteria.default).map(_.length),
        2)
    }
    "Get ReferenceSets" in {
      validateSize(
        dao.getReferenceDataSets(DataSetSearchCriteria.default).map(_.length),
        4)
    }
    "Get BarcodeSets" in {
      validateSize(
        dao.getBarcodeDataSets(DataSetSearchCriteria.default).map(_.length),
        1)
    }
    "Get Projects" in {
      // Should have the GENERAL Project and the Test project created from
      // importing the mock data
      validateSize(dao.getProjects().map(_.length), 2)
    }
    "Sanity Import of Simple DataStore Fasta file" in {
      val now = JodaDateTime.now()
      val tmpFastaFile = Files.createTempFile(tmpJobDir, "mock", ".fasta")
      val dsFile =
        MockFileUtils.writeMockFastaDataStoreFile(1000, tmpFastaFile)

      val jobUUID = UUID.randomUUID()
      val rawEngineJob = MockFileUtils.toTestRawEngineJob("fasta-import-test",
                                                          Some(jobUUID),
                                                          None,
                                                          None)

      val fx = for {
        project <- dao.getProjectByName(TEST_PROJECT_NAME)
        importedJob <- dao.importRawEngineJob(rawEngineJob)
        msg <- dao.importDataStoreFile(dsFile,
                                       jobUUID,
                                       projectId = Some(project.id))
        importedDataStore <- dao.getDataStoreFile(dsFile.uniqueId)
      } yield (importedJob, importedDataStore)

      val (importedJob, dsServiceFile) = Await.result(fx, timeout)
      //dsDataStoreFile.jobId ===
      dsServiceFile.dataStoreFile.path === tmpFastaFile.toAbsolutePath.toString
      dsServiceFile.jobId === importedJob.uuid
    }

    "Sanity Reference DataStore File" in {

      val rsetPath = Files.createTempFile(tmpJobDir, "reference", "set.xml")

      val name = "dataset-references/example_01.xml"

      val px = PacBioTestUtils.getResourcePath(name)
      val rset = DataSetLoader.loadAndResolveReferenceSet(px)

      val rsetUUID = UUID.randomUUID()

      rset.setUniqueId(rsetUUID.toString)

      DataSetWriter.writeReferenceSet(rset, rsetPath)

      val dsFile = MockFileUtils.toTestDataStoreFile(rsetUUID,
                                                     FileTypes.DS_REFERENCE,
                                                     rsetPath)

      val jobUUID = UUID.randomUUID()
      val rawEngineJob = MockFileUtils.toTestRawEngineJob("fasta-import-test",
                                                          Some(jobUUID),
                                                          None,
                                                          None)

      val fxx: Future[(EngineJob, ReferenceServiceDataSet, Int)] = for {
        project <- dao.getProjectByName(TEST_PROJECT_NAME)
        importedJob <- dao.importRawEngineJob(rawEngineJob)
        msg <- dao.importDataStoreFile(dsFile,
                                       jobUUID,
                                       projectId = Some(project.id))
        rSetFile <- dao.getReferenceDataSetById(dsFile.uniqueId)
      } yield (importedJob, rSetFile, project.id)

      val (importedJob, rSetFile, projectId) =
        Await.result(fxx, timeout)

      rSetFile.path === dsFile.path
      rSetFile.projectId === projectId
      rSetFile.jobId === importedJob.id
    }

    "Job search API" in {
      val prefix = Random.alphanumeric.take(10).mkString("")
      def toJobName(x: Int) = s"${prefix}-job$x"
      def toJobNames(xx: Seq[Int]) = xx.map(toJobName).toSet
      val job1 =
        MockFileUtils
          .toTestRawEngineJob("pbsmrtpipe-test",
                              None,
                              Some(JobTypeIds.PBSMRTPIPE),
                              None)
          .copy(name = s"${prefix}-job1")
      val job2 = job1.copy(name = s"${prefix}-job2",
                           uuid = UUID.randomUUID(),
                           state = AnalysisJobStates.SUCCESSFUL)
      val job3 = job2.copy(name = s"${prefix}-job3",
                           uuid = UUID.randomUUID(),
                           smrtlinkVersion = Some("5.1.0"),
                           isActive = false)
      val job4 = job1.copy(name = s"${prefix}-job4",
                           uuid = UUID.randomUUID(),
                           jobTypeId = JobTypeIds.CONVERT_RS_MOVIE.id)
      val c1 = JobSearchCriteria.allAnalysisJobs.copy(
        name = Some(QueryOperators.StringMatchQueryOperator(s"${prefix}-job")))
      val c2 =
        c1.copy(
          state = Some(
            QueryOperators.JobStateEqOperator(AnalysisJobStates.SUCCESSFUL)))
      val c3 = c1.copy(
        smrtlinkVersion = Some(QueryOperators.StringEqQueryOperator("5.1.0")))
      val c4 = c1.copy(isActive = Some(true))
      val c5 = c1.copy(jobTypeId = Some(
        QueryOperators.StringEqQueryOperator(JobTypeIds.CONVERT_RS_MOVIE.id)))
      val c6 = c1.copy(jobTypeId = None)
      val pbsmrtpipeJobs = Seq(job1, job2, job3, job4)
      val searchCriteria = Seq(c1, c2, c3, c4, c5, c6)
      val expectedResults = Seq(
        Seq(3, 2, 1),
        Seq(3, 2),
        Seq(3),
        Seq(2, 1),
        Seq(4),
        Seq(4, 3, 2, 1)
      ).map(toJobNames)
      val fx = for {
        jobs <- Future.sequence(pbsmrtpipeJobs.map { job =>
          dao.importRawEngineJob(job)
        })
        queries <- Future.sequence(searchCriteria.map(c => dao.getJobs(c)))
      } yield queries.map(_.map(_.name).toSet)
      val jobNames = Await.result(fx, timeout)
      jobNames.zip(expectedResults).map {
        case (names1, names2) =>
          names1 === names2
      }
    }

    "Validate Creating and Updating MultiJob" in {

      def runBlock[T](fx: => Future[T]): T = Await.result(fx, timeout)

      def toEp(ix: String) =
        DeferredEntryPoint(FileTypes.DS_SUBREADS.fileTypeId,
                           UUID.randomUUID(),
                           ix)

      val deferredEntryPointIds = Seq("a", "b", "c")
      val deferredEntryPoints: Seq[DeferredEntryPoint] =
        deferredEntryPointIds.map(toEp)

      def toD(n: String): DeferredJob = {
        DeferredJob(deferredEntryPoints,
                    "test.pipeline.id",
                    Nil,
                    Nil,
                    Some(n),
                    None,
                    Some(JobConstants.GENERAL_PROJECT_ID))
      }

      def toJ(m: MultiAnalysisJobOptions): JsObject =
        ServiceJobTypeJsonProtocols.multiAnalysisJobOptionsFormat
          .write(m)
          .asJsObject

      def toEngineEntryPoints(items: Seq[DeferredJob]) =
        items.flatten(_.entryPoints.map(e =>
          EngineJobEntryPointRecord(e.uuid, e.fileTypeId)))

      val childJobNames = Seq("alpha", "beta", "gamma")
      val jobs = childJobNames.map(toD)

      val uuid = UUID.randomUUID()

      val jobName = "MultiJob Test"
      val description = s"$jobName"
      val multiJobOptions =
        MultiAnalysisJobOptions(jobs, Some(jobName), None, None, None)

      val entryPoints = toEngineEntryPoints(multiJobOptions.jobs)

      val f1 = dao.createMultiJob(uuid,
                                  jobName,
                                  description,
                                  JobTypeIds.MJOB_MULTI_ANALYSIS,
                                  entryPoints.toSet,
                                  toJ(multiJobOptions),
                                  None,
                                  None,
                                  None,
                                  childJobs = jobs)

      val createdMultiJob = runBlock(f1)
      createdMultiJob.state === AnalysisJobStates.CREATED
      createdMultiJob.isMultiJob === true

      val createdEntryPoints =
        runBlock(dao.getJobEntryPoints(createdMultiJob.id))
      createdEntryPoints.length === entryPoints.toSet.toList.length

      val children = runBlock(dao.getMultiJobChildren(createdMultiJob.id))
      children.length === childJobNames.length

      // There's a bug in here. This will generate a slick runtime error
      // slick.SlickTreeException: Unreachable reference to s69 after resolving monadic joins
      //val children2 = runBlock(dao.getMultiJobChildren(createdJob.uuid))
      //children2.length === childJobNames.length

      // Test Update
      val updatedName = "Updated Job Name"
      val updatedDescription = "Updated Desc"
      val updateChildJobNames = Seq("epsilon", "delta")
      val updatedChildJobs = updateChildJobNames.map(toD)
      val updatedEntryPoints = toEngineEntryPoints(updatedChildJobs)
      val updatedMultiJobOptions =
        multiJobOptions.copy(name = Some(updatedName),
                             description = Some(updatedDescription),
                             jobs = updatedChildJobs)

      val f2 = dao.updateMultiAnalysisJob(createdMultiJob.id,
                                          updatedMultiJobOptions,
                                          toJ(updatedMultiJobOptions),
                                          None,
                                          None,
                                          None)

      val updatedJob = runBlock(f2)
      updatedJob.name === updatedName

      val updatedChildren = runBlock(dao.getMultiJobChildren(updatedJob.id))
      updatedChildren.length === updateChildJobNames.length
      updatedChildren.map(_.name).toSet ==== updateChildJobNames.toSet

      val updatedCreatedEntryPoints =
        runBlock(dao.getJobEntryPoints(createdMultiJob.id))
      updatedCreatedEntryPoints.length === updatedEntryPoints.toSet.toList.length
    }
  }

  step(cleanUpJobDir(tmpJobDir))
}
