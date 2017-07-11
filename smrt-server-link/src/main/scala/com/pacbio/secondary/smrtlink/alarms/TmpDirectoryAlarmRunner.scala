package com.pacbio.secondary.smrtlink.alarms

import java.nio.file.Paths

import com.pacbio.common.alarms.{AlarmComposer, AlarmRunner}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.file.{FileSystemUtil, FileSystemUtilProvider}
import com.pacbio.common.models.{Alarm, AlarmSeverity, AlarmUpdate}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TmpDirectoryAlarmRunner(fileSystemUtil: FileSystemUtil) extends AlarmRunner {
  override val alarm = Alarm(
    "smrtlink.alarms.tmp_dir",
    "Temp Dir Disk Space",
    "Monitors disk space usage in the tmp dir")

  // TODO(smcclellan): Make this path configurable
  val path = Paths.get("/tmp")

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
    AlarmUpdate(ratio, Some(f"Tmp dir is $percent%.0f%% full."), severity)
  }
}

trait TmpDirectoryAlarmRunnerProvider {
  this: FileSystemUtilProvider with AlarmComposer =>

  val tmpDirectoryAlarmRunner: Singleton[TmpDirectoryAlarmRunner] =
    Singleton(() => new TmpDirectoryAlarmRunner(fileSystemUtil()))

  addAlarm(tmpDirectoryAlarmRunner)
}
