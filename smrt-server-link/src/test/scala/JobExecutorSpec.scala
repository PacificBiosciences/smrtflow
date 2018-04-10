import java.nio.file.{Files, Paths}
import java.util.UUID

import scala.concurrent.duration._
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import akka.actor.{ActorRefFactory, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.{RouteTestTimeout, Specs2RouteTest}
import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors._

import com.pacbio.secondary.smrtlink.dependency.{
  ConfigProvider,
  SetBindings,
  Singleton
}
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.services.utils.StatusGeneratorProvider
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  EngineCoreConfigLoader,
  PbsmrtpipeConfigLoader
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobTask,
  JobTypeIds
}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.auth.{
  JwtUtils,
  JwtUtilsImpl,
  JwtUtilsProvider
}
import com.pacbio.secondary.smrtlink.jobtypes.{
  DeleteSmrtLinkJobOptions,
  ExportSmrtLinkJobOptions,
  PbsmrtpipeJobOptions
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{
  JobsServiceProvider,
  ProjectServiceProvider,
  ServiceComposer
}
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.typesafe.scalalogging.LazyLogging
import org.mockito.internal.matchers.GreaterThan
import slick.jdbc.PostgresProfile.api._

class JobExecutorSpec
    extends Specification
    with Specs2RouteTest
    with JobServiceConstants
    with timeUtils
    with LazyLogging
    with TestUtils {

  sequential

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import CommonModelImplicits._

  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  object TestProviders
      extends ServiceComposer
      with ProjectServiceProvider
      with StatusGeneratorProvider
      with EventManagerActorProvider
      with JobsDaoProvider
      with SmrtLinkTestDalProvider
      with SmrtLinkConfigProvider
      with JobsServiceProvider
      with PbsmrtpipeConfigLoader
      with EngineCoreConfigLoader
      with JwtUtilsProvider
      with ActorSystemProvider
      with ConfigProvider
      with FakeClockProvider
      with EngineCoreJobManagerActorProvider
      with SetBindings {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() =>
      new JwtUtils {
        override def parse(jwt: String): Option[UserRecord] =
          Some(UserRecord(jwt))
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorSystem: Singleton[ActorSystem] = Singleton(system)
    override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
    override val baseServiceId: Singleton[String] = Singleton("test-service")
    override val buildPackage: Singleton[Package] = Singleton(
      getClass.getPackage)

  }

  val dao: JobsDao = TestProviders.jobsDao()
  val totalRoutes = TestProviders.newJobService().prefixedRoutes
  // This needs to be manual triggered here because it doesn't have an explicit dependency.
  val engineManagerActor = TestProviders.engineManagerActor()
  val eventManagerActor = TestProviders.eventManagerActor()

  dao.addListener(engineManagerActor)

  def toJobType(x: String) = s"/$ROOT_SA_PREFIX/job-manager/jobs/$x"
  def toJobTypeById(x: String, i: IdAble) = s"${toJobType(x)}/${i.toIdString}"
  def toJobTypeByIdWithRest(x: String, i: IdAble, rest: String) =
    s"${toJobTypeById(x, i)}/$rest"

  val project = ProjectRequest("mock project name",
                               "mock project description",
                               None,
                               None,
                               None,
                               None)
  var projectId = -1

  val rx = scala.util.Random
  val jobName = s"my-job-name-${rx.nextInt(1000)}"

  val mockOpts = PbsmrtpipeJobOptions(
    Some(jobName),
    None,
    "pbsmrtpipe.pipelines.mock_dev01",
    Seq(BoundServiceEntryPoint("e_01", "PacBio.DataSet.SubreadSet", 1)),
    Nil,
    Nil,
    projectId = Some(-1))

  val jobId = 1
  val taskUUID = UUID.randomUUID()
  val taskTypeId = "smrtflow.tasks.mock_task"
  val mockTaskRecord = CreateJobTaskRecord(taskUUID,
                                           s"$taskTypeId-0",
                                           taskTypeId,
                                           s"task-name-${taskUUID}",
                                           JodaDateTime.now())
  val mockUpdateTaskRecord =
    UpdateJobTaskRecord(taskUUID, "RUNNING", "Updating state to Running", None)

  val url = toJobType("mock-pbsmrtpipe")

  def setup() = {
    val p = TestProviders.engineConfig.pbRootJobDir
    if (!Files.exists(p)) {
      logger.info(s"Creating root job dir $p")
      Files.createDirectories(p)
    }

    setupJobDir(p)
    setupDb(TestProviders.dbConfig)
  }

  step(setup())

  "Job Execution Service list" should {

    var newJob: Option[EngineJob] = None
    val jwtUtilsImpl = new JwtUtilsImpl

    "execute mock-pbsmrtpipe job with project id 1" in {
      val userRecord = UserRecord("jsnow", Some("carbon/jsnow@domain.com"))
      val credentials =
        RawHeader(JwtUtils.JWT_HEADER,
                  jwtUtilsImpl.userRecordToJwt(userRecord))
      val projectRoutes = TestProviders.projectService().prefixedRoutes
      Post(s"/$ROOT_SA_PREFIX/projects", project) ~> addHeader(credentials) ~> projectRoutes ~> check {
        status.isSuccess must beTrue
        projectId = responseAs[FullProject].id
      }

      Post(url, mockOpts.copy(projectId = Some(projectId))) ~> totalRoutes ~> check {
        newJob = Some(responseAs[EngineJob])
        logger.info(s"Response to $url -> $newJob")
        // Hack to poll
        Thread.sleep(10000)
        status.isSuccess must beTrue
        newJob.get.isActive must beTrue
        newJob.get.projectId === projectId
      }
    }
    "access job list by project id" in {
      Get(s"$url") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobs = responseAs[Seq[EngineJob]]
        jobs.size === 1
        jobs.head.name === jobName
      }
      Get(s"$url?projectId=$projectId") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobs = responseAs[Seq[EngineJob]]
        jobs.size === 1
        jobs.head.name === jobName
      }
      Get(s"$url?projectId=${projectId + 1}") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobs = responseAs[Seq[EngineJob]]
        jobs.size === 0
      }
    }
    "access successful job by id" in {
      Get(toJobTypeById(JobTypeIds.MOCK_PBSMRTPIPE.id, 1)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val engineJob = responseAs[EngineJob]
        //println(s"Got job $engineJob")
        engineJob.state === AnalysisJobStates.SUCCESSFUL
        engineJob.jobTypeId === JobTypeIds.MOCK_PBSMRTPIPE.id
      }
    }
    "access job datastore" in {
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val files = responseAs[Seq[DataStoreServiceFile]]
        files.nonEmpty must beTrue
      }
    }
    "update job datastore" in {
      var dsFiles = Seq.empty[DataStoreServiceFile]
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        dsFiles = responseAs[Seq[DataStoreServiceFile]]
        println(s"Got datastore ${dsFiles.length} files from job 1")
        dsFiles.nonEmpty must beTrue
      }

      val uuid = dsFiles.head.uuid
      val r = DataStoreFileUpdateRequest(false, Some("/tmp/foo"), Some(12345))
      Put(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id,
                                1,
                                s"datastore/$uuid"),
          r) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "datastore")) ~> totalRoutes ~> check {
        val dsFiles2 = responseAs[Seq[DataStoreServiceFile]]
        val f = dsFiles2.find(_.uuid == uuid).head
        f.path must beEqualTo("/tmp/foo")
        f.fileSize must beEqualTo(12345)
      }
      val r2 = DataStoreFileUpdateRequest(true, Some(dsFiles.head.path), None)
      // also check central datastore-files endpoint
      Put(s"/$ROOT_SA_PREFIX/$DATASTORE_FILES_PREFIX/$uuid", r2) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "datastore")) ~> totalRoutes ~> check {
        val dsFiles2 = responseAs[Seq[DataStoreServiceFile]]
        val f = dsFiles2.find(_.uuid == uuid).head
        f.path must beEqualTo(dsFiles.head.path)
        f.fileSize must beEqualTo(12345)
      }
    }
    "update job record" in {
      val tags = "alpha,beta.gamma"
      val comment = "Hello, world!"
      val name = "My favorite job"
      val u = UpdateJobRecord(Some(name), Some(comment), Some(tags))
      Put(toJobTypeById(JobTypeIds.MOCK_PBSMRTPIPE.id, 1), u) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val job = responseAs[EngineJob]
        job.name === name
        job.comment === comment
        job.tags === tags
      }
    }
    "access job reports" in {
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "reports")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job events by job id" in {
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job tasks by Int Job Id" in {
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "tasks")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "create a job task by Int Id" in {
      Post(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "tasks"),
           mockTaskRecord) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTask = responseAs[JobTask]
        jobTask.state === "CREATED"
      }
    }
    "validate job task by Int Job Id from job tasks endpoint" in {
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "tasks")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTasks = responseAs[Seq[JobTask]]
        jobTasks.find(_.uuid === mockTaskRecord.uuid).map(_.uuid) must beSome(
          mockTaskRecord.uuid)
      }
    }
    "update a job task status by Job Int Id" in {
      Put(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id,
                                1,
                                s"tasks/${mockTaskRecord.uuid}"),
          mockUpdateTaskRecord) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTask = responseAs[JobTask]
        jobTask.state === mockUpdateTaskRecord.state
      }
    }
    "validate job task was added by Job Int Id" in {
      Get(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 1, "tasks")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTasks = responseAs[Seq[JobTask]]
        jobTasks.find(_.uuid === mockTaskRecord.uuid).map(_.state) must beSome(
          mockUpdateTaskRecord.state)
      }
    }
    "Export job" in {
      val tmpDir = Files.createTempDirectory("export-job")
      val params =
        ExportSmrtLinkJobOptions(Seq(1), tmpDir, false, None, None, None, None)
      Post(toJobType(JobTypeIds.EXPORT_JOBS.id), params) ~> totalRoutes ~> check {
        val jobs = responseAs[EngineJob]
        success
      }
    }
    "Get List of delete job types" in {
      Get(toJobType(JobTypeIds.DELETE_JOB.id)) ~> totalRoutes ~> check {
        val jobs = responseAs[Seq[EngineJob]]
        success
      }
    }
    "create a delete Job and delete a mock-pbsmrtpipe job" in {
      Get(toJobType(JobTypeIds.MOCK_PBSMRTPIPE.id)) ~> totalRoutes ~> check {
        // There must be at least one completed job
        val jobs = responseAs[Seq[EngineJob]]
        // filter(job => AnalysisJobStates.isCompleted(job.state))
        jobs
          .filter(_.id == newJob.get.id)
          .map(_.id)
          .headOption must beSome
      }

      var complete = false
      var retry = 0
      // this job should completely within seconds. Having a large number of retries only makes the test hang
      // without have any feedback that it's running
      val maxRetries = 5 // this job should completely within seconds. Having a large number of retries only makes the test hang
      val startedAt = JodaDateTime.now()
      while (!complete) {
        Get(toJobType(JobTypeIds.MOCK_PBSMRTPIPE.id)) ~> totalRoutes ~> check {
          complete = responseAs[Seq[EngineJob]]
            .filter(_.id == newJob.get.id)
            .head
            .isComplete
          if (!complete && retry < maxRetries) {
            retry = retry + 1
            Thread.sleep(2000)
            println(s"Polling for mock pbsmrtpipe job state ${newJob.get.id}")
            success
          } else if (!complete && retry >= maxRetries) {
            failure(
              s"mock-pbsmrtpipe Job failed to complete after ${computeTimeDelta(JodaDateTime.now, startedAt)} seconds")
          }
        }
      }

      val params = DeleteSmrtLinkJobOptions(newJob.get.uuid,
                                            Some("Job name"),
                                            Some("Job Description"),
                                            removeFiles = true,
                                            dryRun = Some(false),
                                            projectId = Some(projectId),
                                            submit = Some(true))

      Post(toJobType(JobTypeIds.DELETE_JOB.id), params) ~> totalRoutes ~> check {
        // poll hack. We need a general mechanism to wait for a job to complete
        Thread.sleep(50000)
        val job = responseAs[EngineJob]
        job.jobTypeId must beEqualTo(JobTypeIds.DELETE_JOB.id)
        job.projectId must beEqualTo(projectId)
      }
      Get(toJobTypeById(JobTypeIds.MOCK_PBSMRTPIPE.id, newJob.get.id)) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        job.isActive must beFalse
      }
      Get(toJobType(JobTypeIds.MOCK_PBSMRTPIPE.id)) ~> totalRoutes ~> check {
        val jobs = responseAs[Seq[EngineJob]]
        // this really isn't necessary. the test above this is just testing that the isActive is filtered out of the
        // job list
        //jobs.size must beEqualTo(njobs - 1)
        status.isSuccess must beTrue
      }
    }

    //    "Create a Job Event" in {
    //      val r = JobEventRecord("RUNNING", "Task x is running")
    //      Post(toJobTypeByIdWithRest(JobTypeIds.MOCK_PBSMRTPIPE.id, 2, "events"), r) ~> totalRoutes ~> check {
    //        status.isSuccess must beTrue
    //      }
    //    }
  }

  step(cleanUpJobDir(TestProviders.engineConfig.pbRootJobDir))
}
