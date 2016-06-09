import java.util.Date

import com.pacbio.common.actors._
import com.pacbio.common.scheduling.CleanupSchedulerProvider
import com.pacbio.common.dependency.{InitializationComposer, StringConfigProvider, Singleton, SetBindings}
import com.pacbio.common.models.{CleanupJobResponse, ConfigCleanupJobCreate}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.mockito.Mockito.when
import org.specs2.mock._
import org.specs2.mutable.{BeforeAfter, Specification}
import org.specs2.time.NoTimeConversions

import scala.concurrent.Future

class CleanupSpec extends Specification with Mockito with NoTimeConversions {
  sequential

  val TEST_NAME = "TestCleanup"
  val TEST_PATH = "/test/path/*"
  val TEST_SCHEDULE = "0/5 * * * * ?" // run every 5 seconds

  // TODO(smcclellan): Use mock FileSystem instead of mock CleanupDao?
  val mockCleanupDao = mock[CleanupDao]

  object TestProviders extends
      SetBindings with
      InitializationComposer with
      StringConfigProvider with
      CleanupSchedulerProvider with
      CleanupDaoProvider with
      ActorSystemProvider {
    override val actorSystemName = Some("TestSystem")
    override val cleanupDao = Singleton(mockCleanupDao)
    override val configString = Singleton(() =>
      s"""
        |akka {
        |  quartz {
        |    schedules {
        |      $TEST_NAME {
        |        description = "Test cleanup job. Runs every 5 seconds."
        |        expression = "$TEST_SCHEDULE"
        |      }
        |    }
        |  }
        |}
        |
        |cleanup {
        |  $TEST_NAME {
        |    target = "$TEST_PATH"
        |    dryRun = false
        |  }
        |}
      """.stripMargin
    )
  }

  trait SchedulerContext extends BeforeAfter {
    override def before: Any = ()
    override def after: Any = {
      QuartzSchedulerExtension(TestProviders.actorSystem()).shutdown()
    }
  }

  "Cleanup Service" should {
    // TODO(smcclellan): Test service endpoints

    // TODO(smcclellan): Test DAO

    "execute a configured job" in new SchedulerContext {
      val expectedCreate = ConfigCleanupJobCreate(TEST_NAME, TEST_PATH, TEST_SCHEDULE, None, None, Some(false))
      val createResponse = CleanupJobResponse(TEST_NAME, TEST_PATH, TEST_SCHEDULE, None, None, dryRun = false, None, None)

      there was no(mockCleanupDao).runConfigJob(TEST_NAME)

      when(mockCleanupDao.createConfigJob(expectedCreate)).thenReturn(Future(createResponse))
      when(mockCleanupDao.runConfigJob(TEST_NAME)).thenReturn(Future.successful(()))

      val initResults = TestProviders.init()
      initResults.size === 1
      val scheduleResults = initResults.head.asInstanceOf[Seq[(CleanupJobResponse, Date)]]
      scheduleResults.size === 1
      val resp = scheduleResults.head
      resp._1 === createResponse

      there was one(mockCleanupDao).createConfigJob(expectedCreate)

      Thread.sleep(10 * 1000)

      there was atLeastOne(mockCleanupDao).runConfigJob(TEST_NAME)
    }
  }
}
