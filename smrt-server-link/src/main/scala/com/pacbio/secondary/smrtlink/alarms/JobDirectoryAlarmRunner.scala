package com.pacbio.secondary.smrtlink.alarms

import com.pacbio.common.alarms.{AlarmComposer, AlarmRunner}
import com.pacbio.common.dependency.{ConfigProvider, Singleton}
import com.pacbio.common.file.{FileSystemUtil, FileSystemUtilProvider}
import com.pacbio.common.models.{Alarm, AlarmSeverity, AlarmUpdate}
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigConstants, EngineCoreConfigLoader}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class JobDirectoryAlarmRunner(config: Config, fileSystemUtil: FileSystemUtil)
  extends AlarmRunner with EngineCoreConfigLoader{

  override val alarm = Alarm(
    "smrtlink.alarms.job_dir",
    "Job Dir Disk Space",
    "Monitors disk space usage in the job dir")

  // We use the injected config here, instead of engineConfig.pbRootJobDir, so tests can use a custom job dir
  val path = loadJobRoot(config.getString(EngineCoreConfigConstants.PB_ROOT_JOB_DIR))

  override protected def update(): Future[AlarmUpdate] = Future {
    import AlarmSeverity._

    val free: Double = fileSystemUtil.getFreeSpace(path).asInstanceOf[Double]
    val total: Double = fileSystemUtil.getTotalSpace(path).asInstanceOf[Double]

    // Compute ratio of used space to total space, rounding to two decimal places
    val ratio = BigDecimal(1.0 - (free / total)).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    val percent = ratio*100
    val severity: AlarmSeverity = ratio match {
      case r if r >= 0.99 => CRITICAL
      case r if r >= 0.95 => ERROR
      case r if r >= 0.90 => WARN
      case _              => CLEAR
    }
    AlarmUpdate(ratio, Some(f"Job dir is $percent%.0f%% full."), severity)
  }
}

trait JobDirectoryAlarmRunnerProvider {
  this: ConfigProvider with FileSystemUtilProvider with AlarmComposer =>

  val jobDirectoryAlarmRunner: Singleton[JobDirectoryAlarmRunner] =
    Singleton(() => new JobDirectoryAlarmRunner(config(), fileSystemUtil()))

  addAlarm(jobDirectoryAlarmRunner)
}