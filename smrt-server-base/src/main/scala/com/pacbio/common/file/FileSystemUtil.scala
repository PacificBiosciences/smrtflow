package com.pacbio.common.file

import java.io.File
import java.nio.file.Path

import com.pacbio.common.dependency.Singleton

trait FileSystemUtil {
  def getFreeSpace(path: Path): Long
  def getTotalSpace(path: Path): Long
  def getFile(path: Path): File
}

trait FileSystemUtilProvider {
  val fileSystemUtil: Singleton[FileSystemUtil]
}

class JavaFileSystemUtil extends FileSystemUtil {
  override def getFreeSpace(path: Path): Long = getFile(path).getFreeSpace
  override def getTotalSpace(path: Path): Long = getFile(path).getTotalSpace
  override def getFile(path: Path): File = path.toFile
}

trait JavaFileSystemUtilProvider extends FileSystemUtilProvider {
  override val fileSystemUtil: Singleton[FileSystemUtil] = Singleton(() => new JavaFileSystemUtil)
}