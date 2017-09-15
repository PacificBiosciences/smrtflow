package com.pacbio.secondary.smrtlink.alarms

import java.nio.file.Path

import com.pacbio.secondary.smrtlink.file.FileSystemUtil
import com.pacbio.secondary.smrtlink.models.{AlarmSeverity, AlarmUpdate}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 7/13/17.
  */
abstract class DirectoryAlarmRunner(path: Path, fileSystemUtil: FileSystemUtil)
    extends AlarmRunner {

  private def computeUpdate(): AlarmUpdate = {
    import AlarmSeverity._

    val free: Double = fileSystemUtil.getFreeSpace(path).toDouble
    val total: Double = fileSystemUtil.getTotalSpace(path).toDouble

    // Compute ratio of used space to total space, rounding to two decimal places
    val ratio = BigDecimal(1.0 - (free / total))
      .setScale(2, BigDecimal.RoundingMode.HALF_UP)
      .toDouble
    val percent = ratio * 100
    val severity: AlarmSeverity = ratio match {
      case r if r >= 0.99 => AlarmSeverity.CRITICAL
      case r if r >= 0.95 => AlarmSeverity.ERROR
      case r if r >= 0.90 => AlarmSeverity.WARN
      case _ => AlarmSeverity.CLEAR
    }
    AlarmUpdate(ratio,
                Some(f"${alarm.name} is $percent%.0f%% full."),
                severity)
  }

  override protected def update(): Future[AlarmUpdate] = Future {
    if (fileSystemUtil.exists(path)) computeUpdate()
    else
      AlarmUpdate(1.0, Some(s"Unable to find path $path"), AlarmSeverity.ERROR)
  }
}
