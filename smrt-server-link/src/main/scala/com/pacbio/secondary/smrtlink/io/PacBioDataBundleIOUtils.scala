package com.pacbio.secondary.smrtlink.io

import java.io.{File, IOException}
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import sys.process._

import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.common.utils.TarGzUtil
import com.pacbio.secondary.smrtlink.PacBioDataBundleConstants
import com.pacbio.secondary.smrtlink.actors.PacBioBundleUtils
import com.pacbio.secondary.smrtlink.models.{PacBioDataBundle, PacBioDataBundleIO}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.joda.time.{DateTime => JodaDateTime}


import scala.xml.XML

trait PacBioDataBundleIOUtils extends PacBioDataBundleConstants with PacBioBundleUtils with LazyLogging{

  /**
    * Parse the Bundle XML and return a PacBioDataBundle.
    *
    * The manifest.xml *MUST* be in the root directory of the bundle.
    *
    * @param file XML Manifest File
    * @return
    */
  def parseBundleManifestXml(file: File): PacBioDataBundle = {
    val xs = XML.loadFile(file)

    val bundleTypeId = (xs \ "Package").text
    val rawVersion = (xs \ "Version").text
    val author = (xs \ "Author").headOption.map(_.text)
    //val createdAt = (xs \ "Created").text

    PacBioDataBundle(bundleTypeId, rawVersion, JodaDateTime.now(), author)
  }

  def parseBundle(rootDir: Path): PacBioDataBundle =
    parseBundleManifestXml(rootDir.resolve(MANIFEST_FILE).toFile)

  /**
    *
    * Returns the directory where the output bundle was written to. This directory
    * contains the manifest.xml file of the PacBioData Bundle
    *
    * @param file      Raw tgz file of the bundle
    * @param outputDir root dir to extract tgz into (subdir will be created)
    * @return
    */
  def copyFile(file: File, outputDir: File): Path = {
    val tmpFile = File.createTempFile("pb-bundle", EXT_TGZ)
    FileUtils.copyFile(file, tmpFile)

    TarGzUtil.uncompressTarGZ(file, outputDir)

    val outputDirs = outputDir.list().map(x => outputDir.toPath.resolve(x))

    // The untar and zipped output is expected to have a single directory with
    // an manifest.xml file in the root level
    outputDirs
        .find(p => Files.isDirectory(p))
        .getOrElse(throw new IOException("Expected tar.gz have a single directory"))
  }

  def downloadHttpFile(url: URL, outputFile: File): File = {
    (url #> outputFile).!!
    outputFile
  }

  /**
    * This will download the tgz, or tar.gz file and put the
    * output in the output dir. The manifest.xml will be in
    * root directory.
    *
    * @param url URL to download
    * @param outputDir output dir to put untar'ed contents into
    * @return
    */
  def downloadHttp(url: URL, outputDir: File): Path = {
    val tmpFile = File.createTempFile("pb-bundle", EXT_TGZ)
    downloadHttpFile(url, tmpFile)
    TarGzUtil.uncompressTarGZ(tmpFile, outputDir)
    outputDir.toPath
  }

  def downloadGit(url: URL, outputDir: Path): Path = {
    val result = Git.cloneRepository()
        .setURI(url.toURI.toString)
        .setDirectory(outputDir.toFile)
        .call()

    logger.info(s"Downloaded repo $url to $outputDir")
    logger.info(s"Result $result")
    outputDir
  }

  /**
    * This will download the bundle and return the path to
    * the output directory
    *
    * @param url      URI of resource (.tar.gz, .tgz, .git)
    * @param outputDir Root Output Dir
    * @return
    */
  def downloadBundle(url: URL, outputDir: Path): Path = {

    def isGit(sx: String) = sx.endsWith(".git")
    def isTarGz(sx: String) = sx.endsWith(EXT_TGZ) | sx.endsWith(".tgz")

    // Copy the bundle to a temporary directory
    val uuid = UUID.randomUUID()
    val bundleTmpName = s"bundle-$uuid"
    val bundleTmpDir = outputDir.resolve(bundleTmpName)

    val errorMessage = s"Unable to handle URL $url"

    val outputBundle = Option(url.getProtocol) match {
      case Some("http") if isGit(url.getFile) => downloadGit(url, bundleTmpDir)
      case Some("https") if isGit(url.getFile) => downloadGit(url, bundleTmpDir)
      case Some("http") => downloadHttp(url, bundleTmpDir.toFile)
      case Some("file") if isTarGz(url.getFile) => copyFile(Paths.get(url.toURI).toFile, bundleTmpDir.toFile)
      case Some(x) => throw new UnprocessableEntityError(s"$errorMessage with protocol $x")
      case None => throw new UnprocessableEntityError(s"$errorMessage Expected protocols (http,https,file) and extension with .tar.gz or .tgz")
    }

    logger.info(s"downloaded bundle to $outputBundle")
    outputBundle
  }

  /**
    * Download a bundle and copy it to the root bundle directory.
    *
    * The bundles will be placed in {root-bundle-dir}/{uuid}/{my-bundle}
    *
    * Supported Formats: .tgz, tar.gz, .git
    * Supported Protocols: http, file (defaults to file)
    *
    * @param url
    * @param outputDir
    * @return
    */
  def downloadAndParseBundle(url: URL, outputDir: Path): (Path, PacBioDataBundle) = {
    val tmpBundleDir = downloadBundle(url, outputDir)
    val b = parseBundle(tmpBundleDir)
    logger.info(s"Processed bundle $b")
    (tmpBundleDir, b)
  }

  /**
    * Copy a Bundle to the root directory. The bundle will be named within the root dir
    * with the form:
    *
    * {bundle-type-id}-{version}
    *
    * If the directory already exists, an exception will be raised.
    *
    * This is a bit messy. Trying to keep the IO layers separate from the data model.
    *
    * @param bundleSrcDir Source directory of the bundle (often a temp location)
    * @param pacBioBundle Bundle that was parsed from src dir
    * @param rootDir      root output directory. A subdir will be created with the Data Bundle
    * @return
    */
  def copyBundleTo(bundleSrcDir: Path, pacBioBundle: PacBioDataBundle, rootDir: Path): PacBioDataBundleIO = {
    val name = s"${pacBioBundle.typeId}-${pacBioBundle.version}"
    val tgzName = s"$name.tar.gz"
    val bundleDir = rootDir.resolve(name)
    val bundleTgz = rootDir.resolve(tgzName)
    if (Files.exists(bundleDir)) {
      throw new IOException(s"Bundle $name already exists.")
    } else {
      FileUtils.copyDirectory(bundleSrcDir.toFile, bundleDir.toFile)
    }

    // Create companion tgz file
    TarGzUtil.createTarGzip(bundleDir, bundleTgz.toFile)

    PacBioDataBundleIO(bundleTgz, bundleDir, pacBioBundle)
  }

  def getManifestXmlFromDir(path: Path):Option[Path] =
    if (Files.isDirectory(path)) {
      path.toFile.list()
          .find(_ == MANIFEST_FILE)
          .map(x => path.resolve(x))
    } else {
      None
    }


  /**
    * Load bundles from a root directory. Each bundle must adhere to the spec and
    * have a single manifest.xml file in the bundle directory.
    *
    * The bundle directory names must be {BUNDLE_TYPE}-{BUNDLE_VERSION} and there must be a companion a tgz version
    * version in the directory.
    *
    * For example, bundle type "exampleBundle" with bundle version 1.2.3 (from the Version in the manifest.xml)
    *
    * By default the most recent version will be marked as "Active". To override this, create a soft link of
    * {BUNDLE_TYPE}-latest to point to the bundle that is "Active"
    *
    * FIXME, "latest" should be renamed to "active". Latest is a misnomer.
    *
    * root-dir
    *  - exampleBundle-1.2.3
    *  - exampleBundle-1.2.3/manifest.xml
    *  - exampleBundle-1.2.3.tgz
    *  - exampleBundle-latest # soft link to exampleBundle-1.2.3
    *
    * @param path Root Path to the bundle dir
    * @return
    */
  def loadBundlesFromRoot(path: Path): Seq[PacBioDataBundleIO] = {

    // Allow for some looseness when loading from the file system.
    val supportedExts = Seq(".tgz", EXT_TGZ)

    logger.info(s"Attempting to load bundles from $path")
    def hasCompanionTgz(path: Path): Boolean =
      getCompanionTgz(path).isDefined


    def getCompanionTgz(path: Path): Option[Path] = {

      def getIfExists(px: Path): Option[Path] = {
        if (Files.exists(px)) Some(px)
        else None
      }

      supportedExts.map(ext => Paths.get(path.toString + ext))
          .flatMap(getIfExists)
          .headOption
    }

    def isActive(rootBundlePath: Path, bundleTypeId: String): Boolean = {
      val px = rootBundlePath.getParent.resolve(s"$bundleTypeId-$ACTIVE_SUFFIX")
      if (Files.exists(px) && Files.isSymbolicLink(px)) {
        px.toRealPath() == rootBundlePath.toRealPath()
      } else
        false
    }

    // Having trouble composing here. Doing a simple and stupid approach
    def resolveBundleIO(rootBundlePath: Path, tgz: Option[Path], bundle: Option[PacBioDataBundle]): Option[PacBioDataBundleIO] = {
      (tgz, bundle) match {
        case (Some(x), Some(y)) =>
          val isBundleActive = isActive(rootBundlePath, y.typeId)
          Some(PacBioDataBundleIO(x, rootBundlePath, y.copy(isActive = isBundleActive)))
        case _ => None
      }
    }

    def getBundleIO(rootBundlePath: Path): Option[PacBioDataBundleIO] = {
      val tgz = getCompanionTgz(rootBundlePath)
      val b = getManifestXmlFromDir(rootBundlePath)
          .map(px => parseBundleManifestXml(px.toFile))
      resolveBundleIO(rootBundlePath, tgz, b)
    }

    // Look for any subdirectories that have 'manifest.xml' files in them
    // and have a companion tgz directory. See pacbio_bundles.rst and loadBundlesFromRoot for details
    // "Malformed" directories not adhering to the spec will be silently ignored.
    val bundles = path.toAbsolutePath.toFile.list()
        .map(p => path.resolve(p))
        .filter(f => Files.isDirectory(f) & hasCompanionTgz(f))
        .flatMap(px => getBundleIO(px))

    logger.info(s"Successfully loaded ${bundles.length} PacBio Data Bundles.")
    bundles.foreach(b => logger.info(s"BundleType:${b.bundle.typeId} version:${b.bundle.version} isActive:${b.bundle.isActive}"))
    bundles
  }
}

object PacBioDataBundleIOUtils extends PacBioDataBundleIOUtils
