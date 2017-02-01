package com.pacbio.common.actors.alarms

import java.nio.file.{Path, Paths}

import akka.actor.{ActorRef, Props}
import com.pacbio.common.actors.{AlarmDao, AlarmDaoProvider, ActorRefFactoryProvider}
import com.pacbio.common.dependency.{ConfigProvider, Singleton}
import com.pacbio.common.file.{FileSystemUtilProvider, FileSystemUtil}
import com.pacbio.common.models._
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigConstants, EngineCoreConfigLoader}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DiskSpaceAlarm {
  val NAME = "DiskSpaceAlarm"
  // TODO(smcclellan): Make temp dir configurable
  val TMP_DIR = "/tmp"
  val DISK_SPACE_TAG = "smrtlink.alarms.disk_space"
  val TMP_DIR_ID = "smrtlink.alarms.tmp_dir"
  val JOB_DIR_ID = "smrtlink.alarms.job_dir"
}

class DiskSpaceAlarm(alarmDao: AlarmDao, config: Config, fileSystemUtil: FileSystemUtil)
  extends AlarmActor with EngineCoreConfigLoader {

  import DiskSpaceAlarm._
  import AlarmSeverity._

  override val name = NAME

  override def init(): Future[Seq[AlarmMetric]] = {
    val baseCreateMessage = AlarmMetricCreateMessage(
      "",
      "",
      "",
      TagCriteria(),
      MetricType.LATEST,
      Map(WARN -> .9, ERROR -> .95, CRITICAL -> .99),
      windowSeconds = None)

    val creates = Seq(
      baseCreateMessage.copy(
        TMP_DIR_ID,
        "Temp Dir Free Space",
        "Monitors free disk space in the /tmp dir",
        TagCriteria(hasAll = Set(DISK_SPACE_TAG, TMP_DIR_ID))
      ),
      baseCreateMessage.copy(
        JOB_DIR_ID,
        "Job Dir Free Space",
        "Monitors free disk space in the jobs dir",
        TagCriteria(hasAll = Set(DISK_SPACE_TAG, JOB_DIR_ID))
      )
    )

    Future.sequence(creates.map(alarmDao.createAlarmMetric))
  }

  override def update(): Future[Seq[AlarmMetricUpdate]] = {
    // We use the injected config here, instead of engineConfig.pbRootJobDir, so tests can use a custom job dir
    val jobDir = loadJobRoot(config.getString(EngineCoreConfigConstants.PB_ROOT_JOB_DIR))
    val dirs = Map(TMP_DIR_ID -> Paths.get(TMP_DIR), JOB_DIR_ID -> jobDir)
    val updates = dirs.map(a => getMessage(a._1, a._2)).toSeq
    Future.sequence(updates.map(alarmDao.update))
  }

  private def getMessage(tag: String, path: Path): AlarmMetricUpdateMessage = {
    val free: Double = fileSystemUtil.getFreeSpace(path).asInstanceOf[Double]
    val total: Double = fileSystemUtil.getTotalSpace(path).asInstanceOf[Double]
    val ratio = 1.0 - (free / total)
    AlarmMetricUpdateMessage(ratio, tags = Set(DISK_SPACE_TAG, tag), note = None)
  }
}

trait DiskSpaceAlarmProvider {
  this: AlarmDaoProvider with ConfigProvider with FileSystemUtilProvider with ActorRefFactoryProvider with AlarmComposer =>

  val diskSpaceAlarm: Singleton[ActorRef] = Singleton(() =>
    actorRefFactory().actorOf(Props(classOf[DiskSpaceAlarm], alarmDao(), config(), fileSystemUtil()), DiskSpaceAlarm.NAME))

  addAlarm(diskSpaceAlarm)
}
