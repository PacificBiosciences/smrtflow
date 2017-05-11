package com.pacbio.common.utils

import java.nio.file.{Files, Path}
import java.io._

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter
import org.apache.commons.io.filefilter.RegexFileFilter

import scala.collection.JavaConversions._

/**
  * Created by mkocher on 5/1/17.
  */
trait TarGzUtils {

  def uncompressTarGZ(tarFile: File, dest: File): File = {

    dest.mkdir()

    var tarIn:TarArchiveInputStream = null

    try {
      tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tarFile))))
      var tarEntry = tarIn.getNextTarEntry
      while (tarEntry != null) {

        // create a file with the same name as the tarEntry
        val destPath = new File(dest, tarEntry.getName)
        if (tarEntry.isDirectory) {
          destPath.mkdirs()
        }
        else {
          // Create any necessary parent dirs
          val parent = destPath.getParentFile
          if (!Files.exists(parent.toPath)) {
            parent.mkdirs()
          }

          destPath.createNewFile()

          val btoRead = new Array[Byte](1024)
          var bout: BufferedOutputStream = null

          try {
            bout = new BufferedOutputStream(new FileOutputStream(destPath))

            var len = 0
            while (len != -1) {
              len = tarIn.read(btoRead)
              if (len != -1) {
                bout.write(btoRead, 0, len)
              }
            }
          } finally {
            if (bout != null) {
              bout.close()
            }
          }
        }
        tarEntry = tarIn.getNextTarEntry
      }
    } finally {
      if (tarIn != null) {
        tarIn.close()
      }
    }
    dest
  }

  def createTarGzip(inputDirectoryPath: Path, outputFile: File, bufferSize: Int = 4096): File = {

    val fileOutputStream: FileOutputStream = null
    val bufferedOutputStream: BufferedOutputStream = null
    val gzipOutputStream: GzipCompressorOutputStream = null
    val tarArchiveOutputStream: TarArchiveOutputStream = null

    try {

      val fileOutputStream: FileOutputStream = new FileOutputStream(outputFile)
      val bufferedOutputStream: BufferedOutputStream = new BufferedOutputStream(fileOutputStream)
      val gzipOutputStream: GzipCompressorOutputStream = new GzipCompressorOutputStream(bufferedOutputStream)
      val tarArchiveOutputStream: TarArchiveOutputStream = new TarArchiveOutputStream(gzipOutputStream)

      tarArchiveOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
      tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)

      val files = FileUtils.listFiles(inputDirectoryPath.toFile,
        new RegexFileFilter("^(.*?)"),
        DirectoryFileFilter.DIRECTORY)

      for (currentFile <- files) {

        val relativeFilePath: String = new File(inputDirectoryPath.toUri).toURI.relativize(new File(currentFile.getAbsolutePath).toURI).getPath
        val tarEntry: TarArchiveEntry = new TarArchiveEntry(currentFile, relativeFilePath)

        tarEntry.setSize(currentFile.length)
        tarArchiveOutputStream.putArchiveEntry(tarEntry)

        val input = new FileInputStream(currentFile)
        val data = new Array[Byte](bufferSize)
        var nRead = -1
        var nWritten = 0
        while ( {
          nRead = input.read(data); nRead > 0
        }) {
          tarArchiveOutputStream.write(data, 0, nRead)
          nWritten += nRead
        }
        input.close()
        tarArchiveOutputStream.closeArchiveEntry()
      }
      tarArchiveOutputStream.close()
      outputFile

    } finally {
      if (fileOutputStream != null) fileOutputStream.close()
      if (bufferedOutputStream != null) bufferedOutputStream.close()
      if (gzipOutputStream != null) gzipOutputStream.close()
      if (tarArchiveOutputStream != null) tarArchiveOutputStream.close()
    }
  }
}

object TarGzUtils extends TarGzUtils