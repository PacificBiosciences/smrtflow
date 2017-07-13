package com.pacbio.secondary.smrtlink.alarms

import java.nio.file.Path

import com.pacbio.common.file.FileSystemUtil
import com.pacbio.common.models.Alarm


class JobDirectoryAlarmRunner(path: Path, fileSystemUtil: FileSystemUtil) extends DirectoryAlarmRunner(path, fileSystemUtil) {

  override val alarm = Alarm(
    "smrtlink.alarms.job_dir",
    "Job Dir",
    "Monitors disk space usage in the job dir")

}
