import java.nio.file.Paths

import akka.pattern._
import akka.util.Timeout
import com.pacbio.common.actors._
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorImplProvider, JwtUtils, JwtUtilsProvider}
import com.pacbio.secondary.smrtlink.dependency.{DefaultConfigProvider, StringConfigProvider}
import com.pacbio.secondary.smrtlink.file.{FileSystemUtil, FileSystemUtilProvider, JavaFileSystemUtil, JavaFileSystemUtilProvider}
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.AlarmManagerRunnerActor.RunAlarms
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.alarms.TmpDirectoryAlarmRunner
import com.pacbio.secondary.smrtlink.services.{AlarmServiceProvider, ServiceComposer}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._
import scala.concurrent.Await

class AlarmSpec
  extends Specification
  with Mockito
  with Directives
  with Specs2RouteTest
  with NoTimeConversions with LazyLogging{

  sequential

  import Authenticator._
  import AlarmSeverity._
  import com.pacbio.secondary.smrtlink.models.PacBioJsonProtocol._

  val mockFileSystemUtil = mock[FileSystemUtil]

  object TestProviders extends
      ServiceComposer with
      ActorRefFactoryProvider with
      SmrtLinkConfigProvider with
      DefaultConfigProvider with
      ActorSystemProvider with
      FileSystemUtilProvider with
      EngineCoreConfigLoader with
      PbsmrtpipeConfigLoader with
      AlarmDaoActorProvider with
      AlarmRunnerLoaderProvider with
      AlarmManagerRunnerProvider with
      AlarmServiceProvider with
      JavaFileSystemUtilProvider {

    import com.pacbio.secondary.smrtlink.dependency.Singleton

    //override
    override val actorSystemName = Some("TestSystem")
    //override val fileSystemUtil = Singleton(() => mockFileSystemUtil)

  }

  val routes = TestProviders.alarmService().prefixedRoutes
  // This is necessary to trigger creation.
  val alarmManagerRunnerActor = TestProviders.alarmManagerRunnerActor()

  val tmpId = "smrtlink.alarms.tmp_dir"
  val jobId = "smrtlink.alarms.job_dir"

  def runSetup(): Unit = {
    logger.info("Running setup with init message to Alarm Manager")
    implicit val messageTimeout = Timeout(10.seconds)
    val f = (alarmManagerRunnerActor ? RunAlarms).mapTo[MessageResponse]
    val results = Await.result(f, 5.seconds)
    logger.info(s"Results $results")
    // the results are run async, so adding a fudge factor to give time for the alarms to be computed
    Thread.sleep(5000)
  }

  step(runSetup())

  "Alarm Service" should {
    "Alarm Dir Runner compute correct severity" in {
      // All testing of severity resolving is pushed here to a unittest level
      // All service level testing of alarms will only test mechanistically
      val p1 = Paths.get("/tmp-1")
      val p2 = Paths.get("/tmp-2")

      // This is kinda confusing to create a mock from a trait. I believe this is creating an singleton object
      // using the val x = FileSystemUtil {}
      // Any methods used must be explicitly defined in the mockFileSystemUtil.* otherwise there abstract
      val mockFileSystemUtil = mock[FileSystemUtil]

      // Path 1 Completely full
      mockFileSystemUtil.getFreeSpace(p1) returns 0
      mockFileSystemUtil.getTotalSpace(p1) returns 100
      mockFileSystemUtil.exists(p1) returns true

      // Path 2 Half empty
      mockFileSystemUtil.getFreeSpace(p2) returns 50
      mockFileSystemUtil.getTotalSpace(p2) returns 100
      mockFileSystemUtil.exists(p2) returns true

      val r1 = new TmpDirectoryAlarmRunner(p1, mockFileSystemUtil)
      val s1 = Await.result(r1.run(), 10.seconds)
      s1.id === tmpId
      s1.severity === CRITICAL

      // This should be 0
      val r2 = new TmpDirectoryAlarmRunner(p2, mockFileSystemUtil)
      val s2 = Await.result(r2.run(), 10.seconds)
      s2.severity === CLEAR
      s2.value === 0.5

      // Test a path that doesn't exist

      val r3 = new TmpDirectoryAlarmRunner(Paths.get("/tmp-does-not-exist"), new JavaFileSystemUtil)
      val s3 = Await.result(r3.run(), 10.seconds)
      s3.severity == ERROR
      s3.message must beSome("Unable to find path /tmp-does-not-exist")

    }
    "return all alarms" in {
      Get("/smrt-link/alarms") ~> routes ~> check {
        status.isSuccess must beTrue
        val alarms = responseAs[Seq[AlarmStatus]]
        alarms.size === 2
        alarms.find(_.id == tmpId) must beSome
        alarms.find(_.id == jobId) must beSome
      }
    }
    "return a specific alarm for tmp dir" in {
      Get(s"/smrt-link/alarms/$tmpId") ~> routes ~> check {
        status.isSuccess must beTrue
        val alarm = responseAs[AlarmStatus]
        alarm.id === tmpId
      }
    }
    "return a specific alarm status for Job" in {
      Get(s"/smrt-link/alarms/$jobId") ~> routes ~> check {
        status.isSuccess must beTrue
        val alarm = responseAs[AlarmStatus]
        alarm.id === jobId
      }
    }
  }
}
