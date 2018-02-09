package com.pacbio.secondary.smrtlink.analysis.techsupport

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.pacbio.common.models.Constants
import com.pacbio.common.utils.TarGzUtils
import com.pacbio.secondary.smrtlink.analysis.converters.Utils
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobProtocols._
import com.pacbio.secondary.smrtlink.models.EngineJobMetrics
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.input.ReversedLinesFileReader
import org.joda.time.{DateTime => JodaDateTime}
import resource.managed
import spray.json._

trait TechSupportConstants {

  /**
    * In the root level of every TGZ file, there is a
    * manifest that communicates the "type" of bundle.
    */
  val DEFAULT_TS_MANIFEST_JSON = "tech-support-manifest.json"

  /**
    * The default name of the TS TGZ bundle
    */
  val DEFAULT_TS_BUNDLE_TGZ = "tech-support-bundle.tgz"
}

object TechSupportConstants extends TechSupportConstants

trait TechSupportUtils extends TechSupportConstants with LazyLogging {

  // White listed files
  final val JOB_EXTS =
    Set("sh", "stderr", "stdout", "log", "json", "html", "css", "png", "dot")

  private def writeTechSupportTgz[T <: TsManifest](
      manifest: T,
      outputTgz: Path,
      writer: (Path => Unit))(implicit m: JsonFormat[T]): Path = {
    val tempDir = Files.createTempDirectory("ts-manifest")

    val manifestPath =
      tempDir.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile,
                                manifest.toJson.prettyPrint)

    writer(tempDir)

    TarGzUtils.createTarGzip(tempDir, outputTgz.toFile)

    FileUtils.deleteQuietly(tempDir.toFile)

    outputTgz
  }

  def byExt(f: File, extensions: Set[String]): Boolean =
    extensions
      .map(e => f.getName.endsWith(e))
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
      if (!Files.exists(destDir)) {
        FileUtils.forceMkdir(destDir.toFile)
      }
      FileUtils.copyFile(srcP, destP.toFile)
    }

    totalSize
  }

  /**
    * Write the output of a Failed Job to TS TGZ bundle
    *
    * @param jobRoot Path to Failed Job
    * @return
    */
  def writeJobBundleTgz(jobRoot: Path,
                        manifest: TsJobManifest,
                        outputTgz: Path): Path = {

    def writer(path: Path): Unit = {
      TechSupportUtils.copyFilesTo(jobRoot, path, TechSupportUtils.JOB_EXTS)
    }

    writeTechSupportTgz(manifest, outputTgz, writer)
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

    for (reader <- managed(
           new ReversedLinesFileReader(src.toFile, 4096, "UTF-8"));
         writer <- managed(new BufferedWriter(new FileWriter(dest.toFile)))) {

      var numLines = 0

      val b = new StringBuilder()

      while (numLines < maxLines) {
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
      while (it.hasNext) {
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
    val userDataDirs = TS_REQ_INSTALL
      .filter(_ != "log")
      .map(p => smrtLinkUserDataRoot.resolve(p).toAbsolutePath())
      .filter(_.toFile.isDirectory)

    if (userDataDirs.isEmpty) {
      logger.warn(
        s"Unable to find required directories ($TS_REQ_INSTALL) in SL UserRoot $smrtLinkUserDataRoot")
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

    val logFiles: Seq[File] = Utils
      .recursiveListFiles(logDir.toAbsolutePath.toFile, rx)
      .filter(logFilter)

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

  /**
    *
    * Write the SL System Status TS TGZ bundle
    *
    * @param smrtLinkUserDataRoot Path to the SL user data root
    */
  def writeSmrtLinkSystemStatusTgz(smrtLinkSystemId: UUID,
                                   smrtLinkUserDataRoot: Path,
                                   outputTgz: Path,
                                   user: String,
                                   smrtLinkVersion: Option[String],
                                   dnsName: Option[String],
                                   comment: Option[String]): Path = {
    val techSupportBundleId = UUID.randomUUID()
    val manifest = TsSystemStatusManifest(techSupportBundleId,
                                          BundleTypes.SYSTEM_STATUS,
                                          1,
                                          JodaDateTime.now(),
                                          smrtLinkSystemId,
                                          dnsName,
                                          smrtLinkVersion,
                                          user,
                                          comment)

    def writer(path: Path): Unit = {
      TechSupportUtils.copySmrtLinkSystemStatus(smrtLinkUserDataRoot, path)
    }

    writeTechSupportTgz(manifest, outputTgz, writer)
  }

  /**
    * Create a TS TGZ bundle that contains all jobs
    *
    * @param jobs List of EngineJob Metrics to send
    * @return
    */
  def writeSmrtLinkEveHistoryMetrics(smrtLinkSystemId: UUID,
                                     user: String,
                                     smrtLinkVersion: Option[String],
                                     dnsName: Option[String],
                                     comment: Option[String],
                                     outputTgz: Path,
                                     jobs: Seq[EngineJobMetrics]): Path = {

    val jobsJsonName = Paths.get("engine-jobs.json")

    val techSupportBundleId = UUID.randomUUID()
    val manifest = TsJobMetricHistory(techSupportBundleId,
                                      BundleTypes.JOB_HIST,
                                      1,
                                      JodaDateTime.now(),
                                      smrtLinkSystemId,
                                      dnsName,
                                      smrtLinkVersion,
                                      user,
                                      comment,
                                      jobsJsonName)

    def writer(path: Path): Unit = {
      val engineJobMetricsJson = path.resolve(jobsJsonName)
      FileUtils.writeStringToFile(engineJobMetricsJson.toFile,
                                  jobs.toJson.toString())
    }

    writeTechSupportTgz(manifest, outputTgz, writer)

  }

}

object TechSupportUtils extends TechSupportUtils
