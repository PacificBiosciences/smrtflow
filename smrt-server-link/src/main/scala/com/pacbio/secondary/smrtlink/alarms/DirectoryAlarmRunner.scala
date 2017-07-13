package com.pacbio.secondary.smrtlink.alarms

import java.nio.file.Path

import com.pacbio.common.file.FileSystemUtil
import com.pacbio.common.models.AlarmSeverity._
import com.pacbio.common.models.{AlarmSeverity, AlarmUpdate}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 7/13/17.
  */
abstract class DirectoryAlarmRunner(path: Path, fileSystemUtil: FileSystemUtil) extends AlarmRunner{

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
    AlarmUpdate(ratio, Some(f"${alarm.name} is $percent%.0f%% full."), severity)
  }
}
