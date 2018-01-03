package com.pacbio.secondary.smrtlink.file

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Files, Path}

import com.pacbio.secondary.smrtlink.dependency.Singleton
import org.apache.commons.io.FileUtils

trait FileSizeFormatterUtil {

  def humanReadableByteSize(fileSize: Long): String = {
    if (fileSize <= 0) return "0 B"
    // kilo, Mega, Giga, Tera, Peta, Exa, Zetta, Yotta
    val units: Array[String] =
      Array("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroup: Int = (Math.log10(fileSize) / Math.log10(1024)).toInt
    f"${fileSize / Math.pow(1024, digitGroup)}%3.3f ${units(digitGroup)}"
  }
}

trait FileSystemUtil {
  def createDirIfNotExists(p: Path): Path
  def writeToFile(sx: String, path: Path): Path
  def getFreeSpace(path: Path): Long
  def getTotalSpace(path: Path): Long
  def getFile(path: Path): File
  def exists(path: Path): Boolean
}

trait FileSystemUtilProvider {
  val fileSystemUtil: Singleton[FileSystemUtil]
}

trait JFileSystemUtil extends FileSystemUtil {
  override def getFreeSpace(path: Path): Long = getFile(path).getFreeSpace
  override def getTotalSpace(path: Path): Long = getFile(path).getTotalSpace
  override def getFile(path: Path): File = path.toFile
  override def exists(path: Path) = Files.exists(path)

  /**
    * Create directories mkdir -p style
    *
    * @param p Path to directories to create
    * @return
    */
  override def createDirIfNotExists(p: Path): Path = {
    if (!Files.exists(p)) {
      Files.createDirectories(p)
    }
    p
  }

  /**
    * Not sure if this is adding any value.
    *
    * All reading of files should be done from either:
    * - direct calls to FileUtils.readFileToString
    * - this interface.
    *
    * NO MORE usage of Source.fromFile(f).mkString. I don't believe that closes
    * the file handle.
    *
    * @param file
    * @return
    */
  def readFileContents(file: File): String = {
    FileUtils.readFileToString(file, "UTF-8")
  }

  override def writeToFile(sx: String, path: Path) = {
    val bw = new BufferedWriter(new FileWriter(path.toFile))
    bw.write(sx)
    bw.close()
    path
  }
}
object JFileSystemUtil extends JFileSystemUtil

class JavaFileSystemUtil extends JFileSystemUtil {}

trait JavaFileSystemUtilProvider extends FileSystemUtilProvider {
  override val fileSystemUtil: Singleton[FileSystemUtil] = Singleton(
    () => new JavaFileSystemUtil)
}
