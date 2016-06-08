package com.pacbio.common.scheduling

import java.util.Date

import akka.actor._
import akka.pattern.pipe
import com.pacbio.common.actors._
import com.pacbio.common.dependency.{ConfigProvider, InitializationComposer, RequiresInitialization, Singleton}
import com.pacbio.common.models.{CleanupJobResponse, CleanupSize, ConfigCleanupJobCreate}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.typesafe.config.ConfigObject

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object CleanupServiceActor {
  case class RunConfigJob(name: String)
}

class CleanupServiceActor(dao: CleanupDao) extends PacBioActor {
  import CleanupServiceActor._

  def receive: Receive = {
    case RunConfigJob(name) => dao.runConfigJob(name) pipeTo sender
  }
}

/**
 * Example config:
 *
 * akka {
 *   quartz {
 *     defaultTimezone = "America/Los_Angeles"
 *
 *     schedules {
 *       FooCleanup {
 *         description = "A cleanup job that fires off every day at 2:30 AM"
 *         expression = "0 30 2 * * ?"
 *       }
 *     }
 *   }
 * }
 *
 * cleanup {
 *   FooCleanup {
 *     target = "/tmp/foo/\*"
 *     dryRun = false
 *     minSize = "1 GB"
 *     olderThan = "7 days"
 *   }
 * }
 *
 */
class CleanupScheduler(
    actorSystem: ActorSystem,
    cleanupDao: CleanupDao,
    cleanupActor: ActorRef,
    cleanupJobs: Set[ConfigCleanupJobCreate]) extends RequiresInitialization {

  import CleanupServiceActor._

  def init(): Seq[(CleanupJobResponse, Date)] =
    cleanupJobs.map { j =>
      val r = Await.result(cleanupDao.createConfigJob(j), 1.second)
      val d = QuartzSchedulerExtension(actorSystem).schedule(j.name, cleanupActor, RunConfigJob(j.name))
      (r, d)
    }.toSeq
}

trait CleanupSchedulerProvider {
  this: CleanupDaoProvider with ActorSystemProvider with ConfigProvider with InitializationComposer =>

  import scala.collection.JavaConversions._

  val CLEANUP_CONFIG_PATH = "cleanup"
  val TARGET_KEY = "target"
  val OLDER_THAN_KEY = "olderThan"
  val MIN_SIZE_KEY = "minSize"
  val DRY_RUN_KEY = "dryRun"

  val CLEANUP_SCHEDULE_CONFIG_PATH = "akka.quartz.schedules"
  val SCHEDULE_KEY = "expression"

  val cleanupJobs: Singleton[Set[ConfigCleanupJobCreate]] = Singleton(() => {
    var jobs: Set[ConfigCleanupJobCreate] = Set.empty

    if (config().hasPath(CLEANUP_CONFIG_PATH)) {
      val cleanupConfig: ConfigObject = config().getObject(CLEANUP_CONFIG_PATH)
      val scheduleConfig: ConfigObject = config().getObject(CLEANUP_SCHEDULE_CONFIG_PATH)
      val cleanupJobNames: Set[String] = asScalaSet(cleanupConfig.keySet()).toSet

      jobs = cleanupJobNames.map { name =>
        val jobConfig = cleanupConfig.toConfig.getConfig(name)

        val target: String = jobConfig.getString(TARGET_KEY)

        val olderThan: Option[Duration] =
          if (jobConfig.hasPath(OLDER_THAN_KEY))
            Some(Duration(jobConfig.getString(OLDER_THAN_KEY)))
          else None

        val minSize: Option[CleanupSize] =
          if (jobConfig.hasPath(MIN_SIZE_KEY))
            Some(CleanupSize(jobConfig.getString(MIN_SIZE_KEY)))
          else None

        val schedule: String = scheduleConfig.toConfig.getConfig(name).getString(SCHEDULE_KEY)

        val dryRun: Option[Boolean] = if (jobConfig.hasPath(DRY_RUN_KEY)) Some(jobConfig.getBoolean(DRY_RUN_KEY)) else None

        ConfigCleanupJobCreate(name, target, schedule, olderThan, minSize, dryRun)
      }
    }

    jobs
  })


  val cleanupScheduler: Singleton[CleanupScheduler] =
    requireInitialization(Singleton { () =>
      val actorRef = actorRefFactory().actorOf(Props(classOf[CleanupServiceActor], cleanupDao()))
      new CleanupScheduler(actorSystem(), cleanupDao(), actorRef, cleanupJobs())
    })
}
