import java.util.UUID

import akka.actor.ActorRefFactory
import com.pacbio.common.actors.{ActorRefFactoryProvider, InMemoryUserDaoProvider, UserServiceActorRefProvider}
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{SetBindings, Singleton}
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.actors.{JobsDao, JobsDaoActorProvider, JobsDaoProvider, TestDalProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.database.Dal
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{DataSetServiceProvider, JobRunnerProvider, ProjectServiceProvider}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.OAuth2BearerToken
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing.AuthenticationFailedRejection
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._

class ProjectSpec extends Specification
with NoTimeConversions
with Specs2RouteTest
with SetupMockData
with JobServiceConstants
with SmrtLinkConstants {

  sequential

  import SmrtLinkJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(10.seconds)

  val READ_USER_LOGIN = "reader"
  val WRITE_USER_1_LOGIN = "root"
  val WRITE_USER_2_LOGIN = "writer2"
  val INVALID_JWT = "invalid.jwt"
  val READ_CREDENTIALS = OAuth2BearerToken(READ_USER_LOGIN)
  val WRITE_CREDENTIALS_1 = OAuth2BearerToken(WRITE_USER_1_LOGIN)
  val WRITE_CREDENTIALS_2 = OAuth2BearerToken(WRITE_USER_2_LOGIN)
  val INVALID_CREDENTIALS = OAuth2BearerToken(INVALID_JWT)

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
      InMemoryUserDaoProvider with
      UserServiceActorRefProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider with
      SetBindings with
  ActorRefFactoryProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def getJwt(user: ApiUser): String = user.login
      override def validate(jwt: String): Option[String] = if (jwt == INVALID_JWT) None else Some(jwt)
    })

    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(system)
  }

  TestProviders.userDao().createUser(READ_USER_LOGIN, UserRecord("pass"))
  TestProviders.userDao().createUser(WRITE_USER_1_LOGIN, UserRecord("pass"))
  TestProviders.userDao().createUser(WRITE_USER_2_LOGIN, UserRecord("pass"))

  override val dao: JobsDao = TestProviders.jobsDao()
  override val dal: Dal = dao.dal
  val totalRoutes = TestProviders.projectService().prefixedRoutes
  val dbURI = TestProviders.dbURI

  val newProject = ProjectRequest("TestProject", "CREATED", "Test Description")
  val newProject2 = ProjectRequest("TestProject2", "CREATED", "Test Description")

  val newUser = ProjectUserRequest(WRITE_USER_2_LOGIN, "WRITER")
  val newUser2 = ProjectUserRequest(WRITE_USER_2_LOGIN, "READER")

  var newProjId = 0
  var dsCount = 0
  var movingDsId = 0
  var movingDsUuid = UUID.randomUUID()

  def dbSetup() = {
    println("Running db setup")
    logger.info(s"Running tests from db-uri ${dbURI()}")
    runSetup(dao)
    println(s"completed setting up database ${dal.dbURI}")
  }

  textFragment("creating database tables")
  step(dbSetup())

  "Project list" should {
    "reject unauthorized users" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addCredentials(INVALID_CREDENTIALS) ~> totalRoutes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }
    }

    "create a new project" in {
      Post(s"/$ROOT_SERVICE_PREFIX/projects", newProject) ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[Project]
        newProjId = proj.id
        proj.name === newProject.name
      }
    }

    "return a list of all projects" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val projs = responseAs[Seq[Project]]
        projs must contain((p: Project) => p.name === newProject.name)
      }
    }

    "get a specific project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[Project]
        proj.id === newProjId
      }
    }

    "update a project" in {
      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId", newProject2) ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val proj = responseAs[Project]
        proj.name === newProject2.name
      }
    }

    "list initial project users" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val userJson = responseAs[String].parseJson
        userJson match {
          case JsArray(x) => x.length === 1
          case _ => ko
        }
      }
    }

    "add and remove a user to/from a project" in {
      Post(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users", newUser) ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val userJson = responseAs[String].parseJson
        userJson match {
          case JsArray(x) => x.length === 2
          case _ => ko
        }
      }

      Delete(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users/${newUser.login}") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val userJson = responseAs[String].parseJson
        userJson match {
          case JsArray(x) => x.length === 1
          case _ => ko
        }
      }
    }

    "add a user and change change their role in a project" in {
      Post(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users", newUser) ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      def userHasRole(projectUserList: Vector[JsValue], login: String, role: String): Boolean = {
        val nu = projectUserList.filter({
          case JsObject(y) => y("user") match {
            case JsObject(z) => z("login") == JsString(login)
            case _ => false
          }
          case _ => false
        })
        nu.head match {
          case JsObject(y) => y("role") == JsString(role)
          case _ => false
        }
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        // this doesn't work for reasons I don't understand
        // val users = responseAs[Seq[ProjectUserResponse]]

        val userJson = responseAs[String].parseJson
        userJson match {
          case JsArray(userVec) => {
            userVec.length === 2
            userHasRole(userVec, newUser.login, newUser.role) must beTrue
          }
          case _ => ko
        }
      }

      Post(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users", newUser2) ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/users") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue

        val userJson = responseAs[String].parseJson
        userJson match {
          case JsArray(userVec) => {
            userVec.length === 2
            userHasRole(userVec, newUser2.login, newUser2.role) must beTrue
          }
          case _ => ko
        }
      }
    }

    "return a list of datasets in a project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        movingDsId = dsets(0).id
        movingDsUuid = dsets(1).uuid
        dsCount = dsets.size
        dsets.size >= 1
      }
    }

    "move datasets to a new project" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        dsets.size === 0
      }

      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets/$movingDsId") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        dsets.size === 1
      }

      Put(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets/$movingDsUuid") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        dsets.size === 2
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        dsets.size === (dsCount - 2)
      }
    }

    "get projects available to user" in {
      Get(s"/$ROOT_SERVICE_PREFIX/user-projects/$WRITE_USER_1_LOGIN") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val results = responseAs[Seq[UserProjectResponse]]
        results.size === 2 // the general project from dbSetup, plus
                           // the project from the creation test above
      }
    }

    "get projects/datasets available to user" in {
      Get(s"/$ROOT_SERVICE_PREFIX/projects-datasets/$WRITE_USER_1_LOGIN") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val results = responseAs[Seq[ProjectDatasetResponse]]
        results.size === dsCount
      }
    }

    "move datasets back to general project" in {
      Delete(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets/$movingDsId") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        dsets.size === 1
      }

      Delete(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets/$movingDsUuid") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$newProjId/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        dsets.size === 0
      }

      Get(s"/$ROOT_SERVICE_PREFIX/projects/$GENERAL_PROJECT_ID/datasets") ~> addCredentials(WRITE_CREDENTIALS_1) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dsets = responseAs[Seq[DataSetMetaDataSet]]
        dsets.size === dsCount
      }
    }
  }
}
