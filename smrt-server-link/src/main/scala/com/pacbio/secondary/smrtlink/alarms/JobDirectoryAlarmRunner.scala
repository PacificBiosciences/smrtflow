package com.pacbio.secondary.smrtlink.alarms

import java.nio.file.Path

import com.pacbio.secondary.smrtlink.file.FileSystemUtil
import com.pacbio.secondary.smrtlink.models.Alarm

class JobDirectoryAlarmRunner(path: Path, fileSystemUtil: FileSystemUtil)
    extends DirectoryAlarmRunner(path, fileSystemUtil) {

  override val alarm = Alarm(AlarmTypeIds.DIR_JOB,
                             "Job Dir",
                             "Monitors disk space usage in the job dir")

}
