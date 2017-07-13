package com.pacbio.secondary.smrtlink.alarms

import java.nio.file.Path

import com.pacbio.common.file.FileSystemUtil
import com.pacbio.common.models.Alarm


class TmpDirectoryAlarmRunner(tmpDir: Path, fileSystemUtil: FileSystemUtil) extends DirectoryAlarmRunner(tmpDir, fileSystemUtil) {
  override val alarm = Alarm(
    "smrtlink.alarms.tmp_dir",
    "Temp Dir",
    "Monitors disk space usage in the tmp dir")

}