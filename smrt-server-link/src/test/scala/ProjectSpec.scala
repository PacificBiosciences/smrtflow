import akka.actor.ActorRefFactory
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.common.services.{PacBioServiceErrors, ServiceComposer}
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.database.Database
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.actors.{JobsDao, JobsDaoActorProvider, JobsDaoProvider, TestDalProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{DataSetServiceProvider, JobRunnerProvider, ProjectServiceProvider}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing.AuthenticationFailedRejection
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._

class ProjectSpec extends Specification
with NoTimeConversions
with Specs2RouteTest
with SetupMockData
with PacBioServiceErrors
with JobServiceConstants
with SmrtLinkConstants {

  sequential

  import SmrtLinkJsonProtocols._
  import Roles._
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
      ProjectServiceProvider with
      SmrtLinkConfigProvider with
      PbsmrtpipeConfigLoader with
      EngineCoreConfigLoader with
      JobRunnerProvider with
      DataSetServiceProvider with
      JobsDaoActorProvider with
      JobsDaoProvider with
      TestDalProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider with
      SetBindings with
      ActorRefFactoryProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = if (jwt == INVALID_JWT) None else Some {
        if (jwt.startsWith("admin")) UserRecord(jwt, PbAdmin) else UserRecord(jwt)
      }
    })

    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(system)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.projectService().prefixedRoutes
  val dbURI = TestProviders.dbURI()

  val newProject = ProjectRequest("TestProject", "Test Description", Some("CREATED"), None, None)
  val newProject2 = ProjectRequest("TestProject2", "Test Description", Some("ACTIVE"), None, None)
  val newProject3 = ProjectRequest("TestProject3", "Test Description", Some("ACTIVE"), None, None)

  val newUser = ProjectRequestUser(RequestUser(ADMIN_USER_2_LOGIN), "Can Write")
  val newUser2 = ProjectRequestUser(RequestUser(ADMIN_USER_2_LOGIN), "Can Read")

  var newProjId = 0
  var newProjMembers: Seq[ProjectRequestUser] = List()
  var dsCount = 0
  var movingDsId = 0

  def dbSetup() = {
    println("Running db setup")
    logger.info(s"Running tests from db-uri ${dbURI}")
    runSetup(dao)
    println(s"completed setting up database ${dbURI}")
  }

  textFragment("creating database tables")
  step(dbSetup())

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
        newProjMembers = proj.members.map(x => ProjectRequestUser(RequestUser(x.login), x.role))
        newProjId = proj.id
        proj.name === proj.name
        proj.state === "CREATED"
      }
    }

    "fail to create a project with a conflicting name" in {
      Post(s"/$ROOT_SERVICE_PREFIX/projects", newProject) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status === StatusCodes.Conflict
      }
    }

    "return a list of all projects" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val projs = responseAs[Seq[Project]]
        projs must contain((p: Project) => p.name === newProject.name)
      }
    }

    "get a specific project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        proj.id === newProjId
        proj.members.length === 1
        proj.members(0).login === ADMIN_USER_1_LOGIN
        proj.members(0).role === "OWNER"
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

    "add a user and change change their role in a project" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(members = Some(newProjMembers ++ List(newUser)))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val users = responseAs[FullProject].members
        val user = users.filter(_.login == newUser.user.login)
        user(0).role === newUser.role
      }

      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject.copy(members = Some(newProjMembers ++ List(newUser2)))) ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val users = responseAs[FullProject].members
        val user = users.filter(_.login == newUser2.user.login)
        user(0).role === newUser2.role
      }
    }

    "return a list of datasets in a project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[FullProject]
        movingDsId = proj.datasets(0).id
        dsCount = proj.datasets.size
        dsCount >= 1
      }
    }

    "move datasets to a new project" in {
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
    }

    "get projects available to user" in {
      Get(s"/$ROOT_SERVICE_PREFIX/user-projects/$ADMIN_USER_1_LOGIN") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val results = responseAs[Seq[UserProjectResponse]]
        results.size === 3 // the general project from dbSetup, plus
                           // the two projects from earlier tests
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
        dsets.size === 0
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID") ~> addHeader(ADMIN_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[FullProject].datasets
        dsets.size === dsCount
      }
    }
  }
}
