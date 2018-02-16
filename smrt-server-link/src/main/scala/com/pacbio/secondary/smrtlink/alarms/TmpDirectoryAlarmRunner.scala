package com.pacbio.secondary.smrtlink.alarms

import java.nio.file.Path

import com.pacbio.secondary.smrtlink.file.FileSystemUtil
import com.pacbio.secondary.smrtlink.models.Alarm

class TmpDirectoryAlarmRunner(tmpDir: Path, fileSystemUtil: FileSystemUtil)
    extends DirectoryAlarmRunner(tmpDir, fileSystemUtil) {
  override val alarm = Alarm(AlarmTypeIds.DIR_TMP,
                             "Temp Dir",
                             "Monitors disk space usage in the tmp dir")

}
