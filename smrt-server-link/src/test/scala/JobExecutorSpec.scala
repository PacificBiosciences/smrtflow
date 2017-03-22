import java.nio.file.{Files, Paths}
import java.util.UUID

import com.pacbio.common.auth.Authenticator._
import spray.http.HttpHeaders.RawHeader

import scala.concurrent.duration._
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor.{ActorRefFactory, ActorSystem}
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest
import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models._
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.utils.StatusGeneratorProvider
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob,JobTask}
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.services.jobtypes.{DeleteJobServiceTypeProvider, MockPbsmrtpipeJobTypeProvider}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{ProjectServiceProvider, JobManagerServiceProvider, JobRunnerProvider}
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.typesafe.scalalogging.LazyLogging
import slick.driver.PostgresDriver.api._


class JobExecutorSpec extends Specification
with Specs2RouteTest
with NoTimeConversions
with JobServiceConstants with timeUtils with LazyLogging with TestUtils {

  sequential

  import SmrtLinkJsonProtocols._
  import CommonModelImplicits._

  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  object TestProviders extends
  ServiceComposer with
  ProjectServiceProvider with
  JobManagerServiceProvider with
  MockPbsmrtpipeJobTypeProvider with
  DeleteJobServiceTypeProvider with
  JobsDaoActorProvider with
  StatusGeneratorProvider with
  EngineManagerActorProvider with
  EngineDaoActorProvider with
  EventManagerActorProvider with
  JobsDaoProvider with
  TestDalProvider with
  SmrtLinkConfigProvider with
  JobRunnerProvider with
  PbsmrtpipeConfigLoader with
  EngineCoreConfigLoader with
  AuthenticatorImplProvider with
  JwtUtilsProvider with
  InMemoryLogDaoProvider with
  ActorSystemProvider with
  ConfigProvider with
  FakeClockProvider with
  SetBindings {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = Some(UserRecord(jwt))
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorSystem: Singleton[ActorSystem] = Singleton(system)
    override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
    override val baseServiceId: Singleton[String] = Singleton("test-service")
    override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
  }

  val dao: JobsDao = TestProviders.jobsDao()
  val totalRoutes = TestProviders.jobManagerService().prefixedRoutes

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"
  def toJobTypeById(x: String, i: IdAble) = s"${toJobType(x)}/${i.toIdString}"
  def toJobTypeByIdWithRest(x: String, i: IdAble, rest: String) = s"${toJobTypeById(x, i)}/$rest"

  val project = ProjectRequest("mock project name", "mock project description", None, None, None)
  var projectId = -1

  val rx = scala.util.Random
  val jobName = s"my-job-name-${rx.nextInt(1000)}"

  val mockOpts = PbSmrtPipeServiceOptions(
    jobName,
    "pbsmrtpipe.pipelines.mock_dev01",
    Seq(BoundServiceEntryPoint("e_01", "PacBio.DataSet.SubreadSet", Left(1))),
    Nil,
    Nil,
    projectId = -1)

  val jobId = 1
  val taskUUID = UUID.randomUUID()
  val taskTypeId = "smrtflow.tasks.mock_task"
  val mockTaskRecord = CreateJobTaskRecord(taskUUID, s"$taskTypeId-0",  taskTypeId, s"task-name-${taskUUID}", JodaDateTime.now())
  val mockUpdateTaskRecord = UpdateJobTaskRecord(taskUUID, "RUNNING", "Updating state to Running", None)

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

    "execute job" in {
      val credentials = RawHeader(JWT_HEADER, "jsnow")
      val projectRoutes = TestProviders.projectService().prefixedRoutes
      Post(s"/$ROOT_SERVICE_PREFIX/projects", project) ~> addHeader(credentials) ~> projectRoutes ~> check {
        status.isSuccess must beTrue
        projectId = responseAs[FullProject].id
      }

      val url = toJobType("mock-pbsmrtpipe")
      Post(url, mockOpts.copy(projectId = projectId)) ~> totalRoutes ~> check {
        newJob = Some(responseAs[EngineJob])
        logger.info(s"Response to $url -> $newJob")

        status.isSuccess must beTrue
        newJob.get.isActive must beTrue
        newJob.get.projectId === projectId
      }
    }

    "access job by id" in {
      Get(toJobTypeById("mock-pbsmrtpipe", 1)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job datastore" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job reports" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "reports")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job events by job id" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job tasks by Int Job Id" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "tasks")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "create a job task by Int Id" in {
      Post(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "tasks"), mockTaskRecord) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTask = responseAs[JobTask]
        jobTask.state === "CREATED"
      }
    }
    "validate job task by Int Job Id from job tasks endpoint" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "tasks")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTasks = responseAs[Seq[JobTask]]
        jobTasks.find(_.uuid === mockTaskRecord.uuid).map(_.uuid) must beSome(mockTaskRecord.uuid)
      }
    }
    "update a job task status by Job Int Id" in {
      Put(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, s"tasks/${mockTaskRecord.uuid}"), mockUpdateTaskRecord) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTask = responseAs[JobTask]
        jobTask.state === mockUpdateTaskRecord.state
      }
    }
    "validate job task was added by Job Int Id" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "tasks")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val jobTasks = responseAs[Seq[JobTask]]
        jobTasks.find(_.uuid === mockTaskRecord.uuid).map(_.state) must beSome(mockUpdateTaskRecord.state)
      }
    }
    "create a delete Job and delete a mock-pbsmrtpipe job" in {
      var njobs = 0
      Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
        val jobs = responseAs[Seq[EngineJob]]
        njobs = jobs.size
        jobs.count(_.id == newJob.get.id) must beGreaterThan(0)
        njobs must beGreaterThan(0)
      }

      var complete = false
      var retry = 0
      val maxRetries = 30
      val startedAt = JodaDateTime.now()
      while (!complete) {
        Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
          complete = responseAs[Seq[EngineJob]].filter(_.id == newJob.get.id).head.isComplete
          if (!complete && retry < maxRetries) {
            retry = retry + 1
            Thread.sleep(2000)
          } else if (!complete && retry >= maxRetries) {
            failure(s"mock-pbsmrtpipe Job failed to complete after ${computeTimeDelta(JodaDateTime.now, startedAt)} seconds")
          }
        }
      }

      val params = DeleteJobServiceOptions(newJob.get.uuid, removeFiles = true)
      Post(toJobType("delete-job"), params) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        job.jobTypeId must beEqualTo("delete-job")
        job.projectId must beEqualTo(projectId)
      }
      Get(toJobTypeById("mock-pbsmrtpipe", newJob.get.id)) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        job.isActive must beFalse
      }
      Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
        val jobs = responseAs[Seq[EngineJob]]
        // this really isn't necessary. the test above this is just testing that the isActive is filtered out of the
        // job list
        //jobs.size must beEqualTo(njobs - 1)
        status.isSuccess must beTrue
      }
    }

    //    "Create a Job Event" in {
    //      val r = JobEventRecord("RUNNING", "Task x is running")
    //      Post(toJobTypeByIdWithRest("mock-pbsmrtpipe", 2, "events"), r) ~> totalRoutes ~> check {
    //        status.isSuccess must beTrue
    //      }
    //    }
  }

  step(cleanUpJobDir(TestProviders.engineConfig.pbRootJobDir))
}
