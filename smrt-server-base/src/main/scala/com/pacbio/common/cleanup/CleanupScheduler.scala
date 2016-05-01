package com.pacbio.common.cleanup

import akka.actor._
import com.pacbio.common.actors.{CleanupServiceActorRefProvider, ActorSystemProvider, CleanupServiceActor}
import com.pacbio.common.dependency.{ConfigProvider, Singleton}
import com.pacbio.common.models.{CleanupSize, ConfigCleanupJobCreate}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.typesafe.config.ConfigObject

import scala.concurrent.duration.Duration

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
class CleanupScheduler(actorSystem: ActorSystem,
                       cleanupActor: ActorRef,
                       cleanupJobs: Set[ConfigCleanupJobCreate]) {

  import CleanupServiceActor._

  def scheduleAll(): Unit = {
    cleanupJobs.foreach { c =>
      cleanupActor ! CreateConfigJob(c)
      QuartzSchedulerExtension(actorSystem).schedule(c.name, cleanupActor, RunConfigJob(c.name))
    }
  }
}

trait CleanupSchedulerProvider {
  this: ActorSystemProvider with CleanupServiceActorRefProvider with ConfigProvider =>

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
    Singleton(() => new CleanupScheduler(actorSystem(), cleanupServiceActorRef(), cleanupJobs()))
}
