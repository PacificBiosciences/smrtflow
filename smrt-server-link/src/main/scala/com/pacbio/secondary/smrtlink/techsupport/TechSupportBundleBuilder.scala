package com.pacbio.secondary.smrtlink.techsupport

import java.io.{File, IOException}
import java.util.UUID
import java.nio.file.{Files, Path}

import org.joda.time.{DateTime => JodaDateTime}
import org.apache.commons.io.FileUtils

import scala.util.{Failure, Success, Try}
import com.pacbio.common.utils.TarGzUtil
import com.pacbio.common.models.{Constants, PacBioComponentManifest}
import com.pacbio.common.file.JFileSystemUtil
import com.pacbio.secondary.smrtlink.models.ConfigModels.RootSmrtflowConfig
import com.pacbio.secondary.smrtlink.models.{SmrtLinkJsonProtocols, TechSupportBundle}
import spray.json._


/**
  * SL Failed Install
  *
  * All the files within these dirs:
  *
  * $SMRT_ROOT/userdata/{log,config,generated,user_jmsenv}
  *
  * $SMRT_ROOT/current/bundles/smrtlink-analysisservices-gui/current/private/pacbio/smrtlink-analysisservices-gui/wso2am-2.0.0/repository/logs/
  *
  *
  * SL Failed Analysis Job
  *
  * $SMRT_ROOT/userdata/jobs_root/004/004000/logs/
  * $SMRT_ROOT/userdata/jobs_root/004/004000/&#47;stderr
  * $SMRT_ROOT/userdata/jobs_root/004/004000/&#47;stdout
  * $SMRT_ROOT/userdata/jobs_root/004/004000/tasks/&#47;/&#47;stderr
  * $SMRT_ROOT/userdata/jobs_root/004/004000/tasks/&#47;/&#47;stdout
  *
  */
object TechSupportFailedInstallBuilder {

  import SmrtLinkJsonProtocols._

  val DEFAULT_CONFIG_JSON = "smrtlink-system-config.json"
  val DEFAULT_TS_MANIFEST_JSON = "tech-support-manifest.json"
  // Is this assigned by Herb?
  val SMRTLINK_SYSTEM_ID = "smrtlink"


  private def loadManifest(file: File): Seq[PacBioComponentManifest] = {
    val sx = FileUtils.readFileToString(file, "UTF-8")
    sx.parseJson.convertTo[Seq[PacBioComponentManifest]]
  }

  private def loadSmrtLinkVersionFromConfig(file: File): Option[String] = {

    val sx = FileUtils.readFileToString(file, "UTF-8")
    val smrtLinkSystemConfig = sx.parseJson.convertTo[RootSmrtflowConfig]

    smrtLinkSystemConfig.smrtflow.server.manifestFile
        .map(p => loadManifest(p.toFile))
        .flatMap(manifests => manifests.find(m => m.id == SMRTLINK_SYSTEM_ID))
        .map(n => n.version)

  }

  private def writeManifest(m: TechSupportBundle, output: Path): Path = {
    JFileSystemUtil.writeToFile(m.toJson.prettyPrint, output)
    output
  }

  def hasRequiredDir(path: Path): Try[Path] = {
    if (Files.exists(path) && Files.isDirectory(path)) Success(path)
    else Failure(throw new IOException(s"Unable to find required directory $path"))
  }

  def hasRequiredSubdirs(rootPath: Path): Try[Path] = {
    for {
      _ <- hasRequiredDir(rootPath.resolve("userdata"))
      _ <- hasRequiredDir(rootPath.resolve("current"))
    } yield rootPath
  }

  /**
    *
    * 1. Extract the Path to the PacBio Manifest
    * 2. Extract the SL version from the Manifests
    * 3. Copy files to a tmp
    * 4. Write tech-support-manifest.json
    * 5. Create tgz file in output
    *
    * The accepted error handling of a "Failed Install" is bit unclear.
    * This needs to be clarified to add a well-defined expectation of error handling
    * For example, "userdata/log" or "userdata/config" directory doesn't exists.
    * For now, fail in an obvious way at the first error.
    *
    * @param rootSmrtLinkDir Path to root SMRT Link install location
    * @param outputFile output file of tgz file
    * @return
    */
  def apply(rootSmrtLinkDir: Path, outputFile: Path): Path = {

    val techSupportBundleId =  UUID.randomUUID()
    val smrtLinkSystemId = Constants.SERVER_UUID

    val userName = Try {System.getProperty("user.name")}.getOrElse("UNKNOWN")

    val userData = rootSmrtLinkDir.resolve("userdata")
    val current = rootSmrtLinkDir.resolve("current")

    val userDataDirs = Seq("config", "log", "generated", "user_jmsenv").map(p => (p, userData.resolve(p)))

    // This really should be fundamentally fixed to put the log files in the proper location.
    // This just makes the system more difficult to understand and debug. There should be ONE
    // central place for logs. We're not splitting the atom, this shouldn't be that difficult.
    val wso2 = Seq(("wso2", current.resolve("bundles/smrtlink-analysisservices-gui/current/private/pacbio/smrtlink-analysisservices-gui/wso2am-2.0.0/repository/logs")))

    // Nat's original script was grabbing the services stderr and stdout. I'm not doing that here.
    // It should be fundamentally fixed at the log level. Everything from stderr and stdout should be in the logs

    val dirs:Seq[(String, Path)] = userDataDirs ++ wso2

    val configDir = userDataDirs.head

    val tmpDir = Files.createTempDirectory(s"failed-install-$techSupportBundleId")
    val tmpUserData = tmpDir.resolve("userdata")
    Files.createDirectories(tmpUserData)

    // Copy all dirs
    dirs.foreach { d =>
      val (name, path) = d
      val tmpOutputPath = tmpUserData.resolve(name)
      FileUtils.copyDirectory(path.toFile, tmpOutputPath.toFile)
    }

    val smrtLinkSystemConfigPath = configDir._2.resolve(DEFAULT_CONFIG_JSON)
    val smrtLinkSystemVersion = loadSmrtLinkVersionFromConfig(smrtLinkSystemConfigPath.toFile)

    val comment = s"Created by smrtflow version ${Constants.SMRTFLOW_VERSION}"

    // Create and Write Manifest
    val manifest = TechSupportBundle(
      techSupportBundleId,
      TechSupportBundleTypes.FAILED_SL_INSTALL ,
      1,
      JodaDateTime.now(),
      smrtLinkSystemId,
      smrtLinkSystemVersion,
      userName,
      Some(comment))

    val manifestPath = tmpDir.resolve(DEFAULT_TS_MANIFEST_JSON)

    writeManifest(manifest, manifestPath)
    // TGZ the output
    TarGzUtil.createTarGzip(tmpDir, outputFile.toFile)

    FileUtils.deleteQuietly(tmpDir.toFile)

    outputFile
  }
}

