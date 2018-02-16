package com.pacbio.secondary.smrtlink.alarms

import java.io.File
import java.nio.file.{Files, Path}

import com.pacbio.secondary.smrtlink.models.{Alarm, AlarmSeverity, AlarmUpdate}
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.{Future, blocking}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import collection.JavaConverters._

class TmpDirCleanupAlarmRunner(tmpDir: Path) extends AlarmRunner {

  override val alarm: Alarm = Alarm(
    AlarmTypeIds.CLEANUP_TMP_DIR,
    "TempDir Cleanup",
    "Monitors successful Temp Directory cleanup")

  private def toDateTimeFilter(old: JodaDateTime): (Path) => Boolean = {
    path =>
      val lastModified = new JodaDateTime(path.toFile.lastModified())
      lastModified.isBefore(old)
  }

  private def deleteDirIfEmpty(f: File): Unit = {
    if (f.isDirectory) {
      Option(f.listFiles()) match {
        case Some(files) if files.isEmpty => {
          logger.debug(s"Deleting old Directory $f")
          FileUtils.deleteQuietly(f)
        }
        case _ => Unit
      }
    }
  }

  private def deleteFile(f: File): Unit = {
    logger.debug(s"Deleting old tmp file $f")
    FileUtils.deleteQuietly(f)
  }

  private def cleanUpFile(f: File): Unit = {
    if (f.isDirectory) deleteDirIfEmpty(f)
    else deleteFile(f)
  }

  private def cleanUp(rootDir: Path, f: (Path) => Boolean): Future[Int] =
    Future {
      blocking {
        java.nio.file.Files
          .walk(tmpDir)
          .iterator()
          .asScala
          .filter(f)
          .foldLeft(0) { (n, f) =>
            // Ignore the cleanup on the root dir to avoid deleting it
            if (f != rootDir.toFile) {
              cleanUpFile(f.toFile)
              n + 1
            } else {
              0
            }
          }
      }
    }

  override protected def update(): Future[AlarmUpdate] = {
    if (Files.exists(tmpDir)) {
      val now = JodaDateTime.now
      val aWeekAgo = now.minusDays(7)

      cleanUp(tmpDir, toDateTimeFilter(aWeekAgo))
        .map(numFiles =>
          AlarmUpdate(0.0, Some(s"Cleaned up $numFiles resources from Temp Dir"), AlarmSeverity.CLEAR))
        .recover {
          case NonFatal(ex) =>
            AlarmUpdate(
              0.9,
              Some(s"Error cleaning up Temp dir ${ex.getMessage}"),
              AlarmSeverity.ERROR)
        }
    } else {
      Future.successful(
        AlarmUpdate(1.0,
                    Some(s"Temp dir $tmpDir does not exist"),
                    AlarmSeverity.CRITICAL))
    }
  }

}
