package com.pacbio.secondary.analysis.techsupport

import java.io.File
import java.nio.file.{Files, Path}
import java.util.UUID

import com.pacbio.common.models.Constants
import com.pacbio.common.utils.TarGzUtil
import com.pacbio.secondary.analysis.jobs.JobModels.{BundleTypes, TsJobManifest, TsSystemStatusManifest}
import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
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

    TarGzUtil.createTarGzip(tempDir, outputTgz.toFile)

    FileUtils.deleteQuietly(tempDir.toFile)

    outputTgz
  }

  // Required Subdirectories under "/smrtlink-system-root/userdata"
  final val TS_REQ_INSTALL = Seq("config", "log", "generated", "user_jmsenv")
  /**
    *
    * SMRT_ROOT
    *  - /userdata/log
    *  - /userdata/config
    *  - /userdata/generated
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

    // All of the log dirs are configured here.
    val userDataDirs = TS_REQ_INSTALL
        .map(p => smrtLinkUserDataRoot.resolve(p).toAbsolutePath())
        .filter(_.toFile.isDirectory)

    // Nat's original script was grabbing the services stderr and stdout. I'm not doing that here.
    // It should be fundamentally fixed at the log level. Everything from stderr and stdout should be in the logs

    // Copy all dirs
    userDataDirs.foreach { srcPath =>
      val srcRelPath = smrtLinkUserDataRoot.relativize(srcPath)
      val destPath = dest.resolve(srcRelPath)
      FileUtils.forceMkdir(destPath.toFile)
      logger.debug(s"Copying $srcPath to $destPath")
      FileUtils.copyDirectory(srcPath.toFile, destPath.toFile)
    }


    dest
  }

  def writeSmrtLinkSystemStatusTgz(smrtLinkUserDataRoot: Path, dest: Path, user: String, smrtLinkVersion: Option[String], dnsName: Option[String]): Path = {
    val techSupportBundleId =  UUID.randomUUID()
    val smrtLinkSystemId = Constants.SERVER_UUID

    val tmpDir = Files.createTempDirectory(s"sl-status-$techSupportBundleId")

    TechSupportUtils.copySmrtLinkSystemStatus(smrtLinkUserDataRoot, tmpDir)

    val comment = s"Created by smrtflow version ${Constants.SMRTFLOW_VERSION}"

    val manifest = TsSystemStatusManifest(techSupportBundleId, BundleTypes.SYSTEM_STATUS, 1,
      JodaDateTime.now(), smrtLinkSystemId, dnsName, smrtLinkVersion, user, Some(comment))

    val manifestPath = tmpDir.resolve(DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile, manifest.toJson.prettyPrint)

    TarGzUtil.createTarGzip(tmpDir, dest.toFile)

    FileUtils.deleteQuietly(tmpDir.toFile)

    dest
  }


}

object TechSupportUtils extends TechSupportUtils
