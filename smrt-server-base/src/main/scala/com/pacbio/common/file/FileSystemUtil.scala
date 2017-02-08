package com.pacbio.common.file

import java.nio.file.Path

import com.pacbio.common.dependency.Singleton

trait FileSystemUtil {
  def getFreeSpace(path: Path): Long
  def getTotalSpace(path: Path): Long
}

trait FileSystemUtilProvider {
  val fileSystemUtil: Singleton[FileSystemUtil]
}

class JavaFileSystemUtil extends FileSystemUtil {
  override def getFreeSpace(path: Path): Long = path.toFile.getFreeSpace
  override def getTotalSpace(path: Path): Long = path.toFile.getTotalSpace
}

trait JavaFileSystemUtilProvider extends FileSystemUtilProvider {
  override val fileSystemUtil: Singleton[FileSystemUtil] = Singleton(() => new JavaFileSystemUtil)
}