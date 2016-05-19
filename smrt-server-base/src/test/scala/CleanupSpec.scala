import com.pacbio.common.actors._
import com.pacbio.common.cleanup.CleanupSchedulerProvider
import com.pacbio.common.dependency.{StringConfigProvider, Singleton, SetBindings}
import com.pacbio.common.models.ConfigCleanupJobCreate
import org.specs2.mock._
import org.specs2.mutable.Specification

class CleanupSpec extends Specification with Mockito {
  // Tests must be run in sequence because of shared state in InMemoryHealthDaoComponent
  sequential

  val TEST_NAME = "TestCleanup"
  val TEST_PATH = "/test/path/*"
  val TEST_SCHEDULE = "0/5 * * * * ?" // run every 5 seconds

  // TODO(smcclellan): Use mock FileSystem instead of mock CleanupDao
  val mockCleanupDao = mock[CleanupDao]

  object TestProviders extends
      SetBindings with
      StringConfigProvider with
      CleanupSchedulerProvider with
      CleanupServiceActorRefProvider with
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

  "Cleanup Service" should {
    // TODO(smcclellan): Test service endpoints

    // TODO(smcclellan): Test DAO

    "execute a configured job" in {
      there was no(mockCleanupDao).runConfigJob(TEST_NAME)

      TestProviders.cleanupScheduler().scheduleAll()

      val expectedCreate = ConfigCleanupJobCreate(TEST_NAME, TEST_PATH, TEST_SCHEDULE, None, None, Some(false))
      there was one(mockCleanupDao).createConfigJob(expectedCreate)

      Thread.sleep(10 * 1000)

      there was atLeastOne(mockCleanupDao).runConfigJob(TEST_NAME)
    }
  }
}
