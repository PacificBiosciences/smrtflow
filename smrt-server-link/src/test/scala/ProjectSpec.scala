import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRefFactory
import com.pacbio.secondary.smrtlink.actors.ActorSystemProvider
import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.secondary.smrtlink.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.services.{JobsServiceProvider, PacBioServiceErrors, ProjectServiceProvider, ServiceComposer}
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.SimpleDevJobOptions
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{EngineJob, JobTypeIds}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing.{AuthenticationFailedRejection, AuthorizationFailedRejection}
import spray.testkit.Specs2RouteTest
import slick.driver.PostgresDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration._

class ProjectSpec extends Specification
with NoTimeConversions
with Specs2RouteTest
with SetupMockData
with PacBioServiceErrors
with JobServiceConstants
with SmrtLinkConstants with TestUtils{

  sequential

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import Authenticator._

  implicit val routeTestTimeout = RouteTestTimeout(10.seconds)

  val READ_USER_LOGIN = "reader"
  val ADMIN_USER_1_LOGIN = "admin1"
  val ADMIN_USER_2_LOGIN = "admin2"
  val INVALID_JWT = "invalid.jwt"
  val READ_CREDENTIALS = RawHeader(JWT_HEADER, READ_USER_LOGIN)
  val ADMIN_CREDENTIALS_1 = RawHeader(JWT_HEADER, ADMIN_USER_1_LOGIN)
  val ADMIN_CREDENTIALS_2 = RawHeader(JWT_HEADER, ADMIN_USER_2_LOGIN)
  val INVALID_CREDENTIALS = RawHeader(JWT_HEADER, INVALID_JWT)

  object TestProviders extends
      ServiceComposer with
      ActorSystemProvider with
      ConfigProvider with
      JobsServiceProvider with
      ProjectServiceProvider with
      SmrtLinkConfigProvider with
      PbsmrtpipeConfigLoader with
      EngineCoreConfigLoader with
      EventManagerActorProvider with
      JobsDaoProvider with
      TestDalProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider with
      SetBindings with
      ActorRefFactoryProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = if (jwt == INVALID_JWT) None else Some(UserRecord(jwt))
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(system)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.routes()

  val newProject = ProjectRequest("TestProject", "Test Description", Some(ProjectState.CREATED), None, None, Some(List(ProjectRequestUser(ADMIN_USER_1_LOGIN, ProjectUserRole.OWNER))))
  val newProject2 = ProjectRequest("TestProject2", "Test Description", Some(ProjectState.ACTIVE), None, None, None)
  val newProject3 = ProjectRequest("TestProject3", "Test Description", Some(ProjectState.ACTIVE), None, None, Some(List(ProjectRequestUser(ADMIN_USER_1_LOGIN, ProjectUserRole.OWNER))))
  val newProject4 = ProjectRequest("TestProject4", "Test Description", Some(ProjectState.ACTIVE), Some(ProjectRequestRole.CAN_VIEW), None, Some(List(ProjectRequestUser(ADMIN_USER_1_LOGIN, ProjectUserRole.OWNER))))

  val newUser = ProjectRequestUser(ADMIN_USER_2_LOGIN, ProjectUserRole.CAN_EDIT)
  val newUser2 = ProjectRequestUser(ADMIN_USER_2_LOGIN, ProjectUserRole.CAN_VIEW)

  var newProjId = 0
  var newProjMembers: Seq[ProjectRequestUser] =
    List(ProjectRequestUser(ADMIN_USER_2_LOGIN, ProjectUserRole.OWNER))
  var dsCount = 0
  var movingDsId = 0

  lazy val rootJobDir = TestProviders.jobEngineConfig().pbRootJobDir

  step(setupJobDir(rootJobDir))
  step(setupDb(TestProviders.dbConfig))
  //FIXME(mpkocher)(2016-12-16) I believe this only needs to import a few SubreadSets.
  step(runInsertAllMockData(dao))

  "Project list" should {
    "reject unauthorized users" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addHeader(INVALID_CREDENTIALS) ~> totalRoutes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }
    }

    "create a new project" in {
      Post(s"/$ROOT_SERVICE_PREFIX/projects", newProject) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        newProjMembers = proj.members.map(x => ProjectRequestUser(x.login, x.role))
        newProjId = proj.id
        proj.name === proj.name
        proj.state === ProjectState.CREATED
        proj.grantRoleToAll.isEmpty must beTrue
      }
    }

    "read the new project as the owner" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }

    "fail to read the new project as a non-member" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }

    "fail to delete the new project as a non-member" in {
      Delete(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }

    "fail to create a project with a conflicting name" in {
      Post(s"/$ROOT_SERVICE_PREFIX/projects", newProject) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status === StatusCodes.Conflict
      }
    }

    "return a list of user 1 projects" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val projs = responseAs[Seq[Project]]
        projs.map(_.id) must contain(newProjId)
      }
    }

    "return a list of user 2 projects" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val projs = responseAs[Seq[Project]]
        projs.map(_.id) must not contain(newProjId)
      }
    }

    "get a specific project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.id === newProjId
        proj.members.length === 1
        proj.members.head.login === ADMIN_USER_1_LOGIN
        proj.members.head.role === ProjectUserRole.OWNER
      }
    }

    "update a project" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject2.copy(members = Some(newProjMembers))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.name === newProject2.name
        proj.state === newProject2.state.get
      }
    }

    "fail to update a project as a non-member" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject2.copy(members = Some(newProjMembers))) ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }

    "fail to update a project with an unknown user role" in {
      import spray.http.{ContentTypes, HttpEntity}
      import ContentTypes.`application/json`
      import org.specs2.execute.FailureException

      val newProjectJson =
        """
          |{
          |  "name": "TestProject2",
          |  "description": "Test Description",
          |  "state": "ACTIVE",
          |  "members": [{"user": {"login": "jsnow"}, "role": "BAD_ROLE"}]
          |}
        """.stripMargin
      val newProjectEntity = HttpEntity(`application/json`, newProjectJson)

      try {
        Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProjectEntity) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
          failure(s"Request should have failed but response was $response")
        }
      } catch {
        case e: FailureException if e.getMessage().contains("MalformedRequestContentRejection") =>
      }
      ok
    }

    "fail to update a project with an unknown state" in {
      import spray.http.{ContentTypes, HttpEntity}
      import ContentTypes.`application/json`
      import org.specs2.execute.FailureException

      val newProjectJson =
        """
          |{
          |  "name": "TestProject2",
          |  "description": "Test Description",
          |  "state": "BAD_STATE",
          |  "members": []
          |}
        """.stripMargin
      val newProjectEntity = HttpEntity(`application/json`, newProjectJson)

      try {
        Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProjectEntity) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
          failure(s"Request should have failed but response was $response")
        }
      } catch {
        case e: FailureException if e.getMessage().contains("MalformedRequestContentRejection") =>
      }
      ok
    }

    "fail to update a project with a no owners" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject2.copy(members = Some(Seq()))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status === StatusCodes.Conflict
      }
    }

    "fail to update a project with a conflicting name" in {
      var confProjId = 0

      Post(s"/$ROOT_SERVICE_PREFIX/projects", newProject3) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        confProjId = proj.id
      }

      Put(s"/$ROOT_SERVICE_PREFIX/projects/$confProjId", newProject3.copy(name = newProject2.name)) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status === StatusCodes.Conflict
      }
    }

    "add and remove a user to/from a project" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(members = Some(newProjMembers ++ List(newUser)))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.id === newProjId
        proj.members.length === 2
      }

      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(members = Some(newProjMembers))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.id === newProjId
        proj.members.length === 1
      }
    }

    "add a user and change their role in a project" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(members = Some(newProjMembers ++ List(newUser)))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val users = responseAs[FullProject].members
        val user = users.filter(_.login == newUser.login).head
        user.role === newUser.role
      }

      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(members = Some(newProjMembers ++ List(newUser2)))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val users = responseAs[FullProject].members
        val user = users.filter(_.login == newUser2.login).head
        user.role === newUser2.role
      }
    }

    "return a non-empty list of datasets in a project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        dsCount = proj.datasets.size
        dsCount >= 1
      }
    }

    "move datasets to a new project" in {
      val jobType = JobTypeIds.SIMPLE
      val opts = SimpleDevJobOptions(1, 7)
      val jsonSettings = opts.toJson.asJsObject
      val jobUUID = UUID.randomUUID()
      val jobName = "test"
      val jobDescription = "description"

      val jobFut = for {
        // getting datasets here because the project dataset list
        // doesn't include the dataset type
        ds <- dao.getSubreadDataSets(projectIds = List(GENERAL_PROJECT_ID))
        dsToMove = ds.head
        entryPoint = EngineJobEntryPointRecord(dsToMove.uuid, dsToMove.datasetType)
        jobToMove <- dao.createJob2(jobUUID, jobName, jobDescription, jobType, Seq(entryPoint), jsonSettings, None, None, None)
      } yield (jobToMove, dsToMove)

      val (jobToMove, dsToMove) = Await.result(jobFut, 30 seconds)

      movingDsId = dsToMove.id

      Get(s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/${jobType.id}/${jobToMove.id}") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val movedJob = responseAs[EngineJob]
        movedJob.projectId === GENERAL_PROJECT_ID
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.datasets.size === 0
      }

      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(datasets = Some(List(RequestId(movingDsId))))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.datasets.size === 1
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.datasets.size === (dsCount - 1)
      }

      Get(s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$jobType/${jobToMove.id}") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val movedJob = responseAs[EngineJob]
        movedJob.id === jobToMove.id
        movedJob.projectId === newProjId
      }
    }

    "get projects available to user" in {
      Get(s"/$ROOT_SERVICE_PREFIX/user-projects/$ADMIN_USER_1_LOGIN") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val results = responseAs[Seq[UserProjectResponse]]
        results.size must beGreaterThanOrEqualTo(3) // the general project from dbSetup and the two projects from earlier tests
      }
    }

    "get projects/datasets available to user" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects-datasets/$ADMIN_USER_1_LOGIN") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val results = responseAs[Seq[ProjectDatasetResponse]]
        results.size === dsCount
      }
    }

    "round trip a project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]

        Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", proj.asRequest) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
          status.isSuccess must beTrue
          val roundTripProj = responseAs[FullProject]
          proj.name === roundTripProj.name
          proj.description === roundTripProj.description
          proj.state === roundTripProj.state
          proj.datasets.map(_.id) === roundTripProj.datasets.map(_.id)
          proj.members.map(_.login) === roundTripProj.members.map(_.login)
          proj.members.map(_.role) === roundTripProj.members.map(_.role)
        }
      }
    }

    "move datasets back to general project" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(datasets = Some(List()))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[FullProject].datasets
        dsets must beEmpty
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[FullProject].datasets
        dsets.size === dsCount
      }
    }

    "delete a project" in {
      // first move a dataset into the project
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(datasets = Some(List(RequestId(movingDsId))))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      // check that the general project has one fewer dataset
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[FullProject].datasets
        dsets.size === (dsCount - 1)
      }

      // check that the project list contains the project we're going to delete
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val projs = responseAs[Seq[Project]]
        projs.map(_.id) must contain(newProjId)
      }

      // then delete the project
      Delete(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      // check that the project list no longer contains that project
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val projs = responseAs[Seq[Project]]
        projs.map(_.id) must not contain(newProjId)
      }

      // check that the general project regained a dataset
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[FullProject].datasets
        dsets.size === dsCount
        dsets.filter(_.id == movingDsId).head.isActive must beFalse
      }
    }

    "create a project that grants a role to all users" in {
      Post(s"/$ROOT_SERVICE_PREFIX/projects", newProject4) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        newProjId = proj.id
        proj.grantRoleToAll === Some(ProjectUserRole.CAN_VIEW)
      }
    }

    "view a project that grants view rights to all users as a non-member" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.id === newProjId
        proj.grantRoleToAll === Some(ProjectUserRole.CAN_VIEW)
        proj.members.length === 1
        proj.members.head.login === ADMIN_USER_1_LOGIN
        proj.members.head.role === ProjectUserRole.OWNER
      }
    }

    "view a project that grants view rights to all users as a non-member in a list" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val projs = responseAs[Seq[Project]]
        projs.map(_.id) must contain(newProjId)
      }
    }

    "fail to update a project that grants view rights to all users as a non-member" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject4) ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }

    "revoke role granted to all users" in {
      val update = newProject4.copy(grantRoleToAll = Some(ProjectRequestRole.NONE), state = None, members = None)
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", update) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.id === newProjId
        proj.grantRoleToAll === None
      }
    }

    "fail to view a project that revoked view rights fom all users as a non-member" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_2) ~> totalRoutes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }
  }
}
