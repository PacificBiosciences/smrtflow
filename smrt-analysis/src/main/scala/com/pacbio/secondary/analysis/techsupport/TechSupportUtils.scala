package com.pacbio.secondary.analysis.techsupport

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Files, Path}
import java.util.UUID

import com.pacbio.common.models.Constants
import com.pacbio.common.utils.TarGzUtils
import com.pacbio.secondary.analysis.converters.Utils
import com.pacbio.secondary.analysis.jobs.JobModels.{BundleTypes, TsJobManifest, TsSystemStatusManifest}
import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.input.ReversedLinesFileReader
import org.joda.time.{DateTime => JodaDateTime}
import resource.managed
import spray.json._

trait TechSupportConstants {
  val DEFAULT_TS_MANIFEST_JSON = "tech-support-manifest.json"
  val DEFAULT_TS_BUNDLE_TGZ = "tech-support-bundle.tgz"
}

object TechSupportConstants extends TechSupportConstants

trait TechSupportUtils extends TechSupportConstants with LazyLogging{

  // White listed files
  final val JOB_EXTS = Set("sh", "stderr", "stdout", "log", "json", "html", "css", "png", "dot")

  def byExt(f: File, extensions: Set[String]): Boolean =
    extensions.map(e => f.getName.endsWith(e))
      .reduceOption(_ || _)
      .getOrElse(false)

  def recursiveListFiles(f: File, fx: (File => Boolean)): Array[File] = {
    val these = f.listFiles
    val good = these.filter(_.isFile).filter(fx)
    good ++ these.filter(_.isDirectory).flatMap(recursiveListFiles(_, fx))
  }

  /**
    * Filter by extension for files from src directory to a dest directory
    *
    * @param src Source Directory
    * @param dest Destination directory (will be created if doesn't exist)
    * @param extensions Set of extensions that will filter
    * @return
    */
  def copyFilesTo(src: Path, dest: Path, extensions: Set[String]): Long = {

    if (!Files.exists(dest)) {
      FileUtils.forceMkdir(dest.toFile)
    }

    val fx = byExt(_: File, JOB_EXTS)

    val files = recursiveListFiles(src.toFile, fx)

    val destFiles = files.map { f =>
      val sx = src.relativize(f.toPath)
      val destF = dest.resolve(sx).toAbsolutePath
      (f, destF)
    }

    val totalSize = destFiles.map(x => x._1.length()).sum

    destFiles.foreach { x =>
      val (srcP, destP) = x
      val destDir = destP.getParent
      if (! Files.exists(destDir)) {
        FileUtils.forceMkdir(destDir.toFile)
      }
      FileUtils.copyFile(srcP, destP.toFile)
    }

    totalSize
  }

  def writeJobBundleTgz(jobRoot: Path, manifest: TsJobManifest, outputTgz: Path): Path = {

    val tempDir = Files.createTempDirectory("ts-manifest")

    val manifestPath = tempDir.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile, manifest.toJson.prettyPrint)

    TechSupportUtils.copyFilesTo(jobRoot, tempDir, TechSupportUtils.JOB_EXTS)

    TarGzUtils.createTarGzip(tempDir, outputTgz.toFile)

    FileUtils.deleteQuietly(tempDir.toFile)

    outputTgz
  }


  /**
    * Copy the last N lines of a src to a dest file
    *
    * @param src      Source log file
    * @param dest     Dest log file
    * @param maxLines Maximum number of lines to copy for source
    */
  def copyLastLines(src: Path, dest: Path, maxLines: Int = 2000): Long = {

    logger.info(s"Log file ${src.toFile.length()} bytes src: $src")

    for (reader <- managed(new ReversedLinesFileReader(src.toFile, 4096, "UTF-8"));
         writer <- managed(new BufferedWriter(new FileWriter(dest.toFile)))) {

      var numLines = 0

      val b = new StringBuilder()

      while(numLines < maxLines) {
        val sx = reader.readLine()
        if (sx != null) {
          b.insert(0, sx + "\n")
          numLines += 1
        } else {
          logger.debug(s"Read in $numLines lines from $src")
          numLines = maxLines
        }
      }


      val it = b.iterator
      while(it.hasNext) {
        val item = it.next()
        writer.write(item)
      }

    }

    logger.info(s"Log file ${dest.toFile.length()} bytes src: $dest")
    dest.toFile.length()
  }


  // Required Subdirectories under "/smrtlink-system-root/userdata"
  final val TS_REQ_INSTALL = Seq("config", "log", "generated", "user_jmsenv")
  /**
    *
    * SMRT_ROOT
    *  - /userdata/log
    *  - /userdata/config
    *  - /userdata/generated
    *  - /userdata/user_jmsenv
    *
    *
    * @param smrtLinkUserDataRoot Root SMRT Link System Dir (This must contain a "userdata" subdirectory
    * @param dest            Destination to write files to
    * @return
    */
  def copySmrtLinkSystemStatus(smrtLinkUserDataRoot: Path, dest: Path): Path = {

    if (!Files.exists(dest)) {
      FileUtils.forceMkdir(dest.toFile)
    }

    // Copy the config and general info. These should only contain small files.
    // The logs will be handled in a separate case below
    val userDataDirs = TS_REQ_INSTALL.filter(_ != "log")
        .map(p => smrtLinkUserDataRoot.resolve(p).toAbsolutePath())
        .filter(_.toFile.isDirectory)

    if (userDataDirs.isEmpty) {
      logger.warn(s"Unable to find required directories ($TS_REQ_INSTALL) in SL UserRoot $smrtLinkUserDataRoot")
    }

    // Copy all dirs
    userDataDirs.foreach { srcPath =>
      val srcRelPath = smrtLinkUserDataRoot.relativize(srcPath)
      val destPath = dest.resolve(srcRelPath)
      FileUtils.forceMkdir(destPath.toFile)
      logger.debug(s"Copying $srcPath to $destPath")
      FileUtils.copyDirectory(srcPath.toFile, destPath.toFile)
    }

    // Copy all files within the log dir that end in .log.
    // Handle the special case of http_access_*.log where these aren't rolled over in wso2

    def logFilter(f: File) = !f.getName.startsWith("http_access_")

    val logDir = smrtLinkUserDataRoot.resolve("log").toAbsolutePath()

    val rx = """\.log$""".r

    val logFiles:Seq[File] = Utils.recursiveListFiles(logDir.toAbsolutePath.toFile, rx).filter(logFilter)

    logger.info(s"Found ${logFiles.length} log files to copy")

    var total = 0L
    logFiles.foreach { srcFile =>
      val srcPath = srcFile.toPath
      val srcRelPath = smrtLinkUserDataRoot.relativize(srcPath)
      val destPath = dest.resolve(srcRelPath)
      FileUtils.forceMkdir(destPath.getParent.toFile)
      val logSize = copyLastLines(srcPath, destPath)
      total += logSize
    }

    logger.info(s"Wrote $total bytes to log files in root $dest")
    dest
  }

  def writeSmrtLinkSystemStatusTgz(smrtLinkSystemId: UUID, smrtLinkUserDataRoot: Path, dest: Path, user: String, smrtLinkVersion: Option[String], dnsName: Option[String]): Path = {
    val techSupportBundleId =  UUID.randomUUID()

    val tmpDir = Files.createTempDirectory(s"sl-status-$techSupportBundleId")

    TechSupportUtils.copySmrtLinkSystemStatus(smrtLinkUserDataRoot, tmpDir)

    val comment = s"Created by smrtflow version ${Constants.SMRTFLOW_VERSION}"

    val manifest = TsSystemStatusManifest(techSupportBundleId, BundleTypes.SYSTEM_STATUS, 1,
      JodaDateTime.now(), smrtLinkSystemId, dnsName, smrtLinkVersion, user, Some(comment))

    val manifestPath = tmpDir.resolve(DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile, manifest.toJson.prettyPrint)

    TarGzUtils.createTarGzip(tmpDir, dest.toFile)

    FileUtils.deleteQuietly(tmpDir.toFile)

    dest
  }


}

object TechSupportUtils extends TechSupportUtils
