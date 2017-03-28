package com.pacbio.secondary.smrtlink.services

import java.io.{File, IOException}
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import collection.JavaConversions._
import collection.JavaConverters._
import scala.xml._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent._
import sys.process._
import collection.mutable
import spray.http.HttpHeaders
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._
import akka.actor.ActorSystem
import com.pacbio.common.actors.ActorSystemProvider

import scala.util.{Failure, Success, Try}
import org.joda.time.{DateTime => JodaDateTime}
import org.eclipse.jgit.api.Git
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.common.services._
import com.pacbio.common.utils.TarGzUtil
import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import spray.routing.directives.FileAndResourceDirectives

import scala.util.control.NonFatal

trait BundleConstants {
  val FILE = "definitions/PacBioAutomationConstraints.xml"
  val MANIFEST_FILE = "manifest.xml"
  // This needs to be changed to "active"
  val ACTIVE_SUFFIX = "latest"
  // For consistency any bundle writing or reading will use the extension format
  val EXT_TGZ = ".tar.gz"
}


trait BundleUtils extends BundleConstants with LazyLogging{

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
        px.toRealPath() == rootBundlePath
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


  def getBundlesByType(bundles: Seq[PacBioDataBundle], bundleType: String): Seq[PacBioDataBundle] =
    bundles.filter(_.typeId == bundleType)

  /**
    * Return the newest version of bundle as defined by SemVer
    * @param bundleType
    * @return
    */
  def getNewestBundleVersionByType(bundles: Seq[PacBioDataBundle], bundleType: String): Option[PacBioDataBundle] = {
    implicit val orderBy = PacBioDataBundle.orderByBundleVersion
    getBundlesByType(bundles, bundleType).sorted.reverse.headOption
  }

  def getBundle(bundles: Seq[PacBioDataBundle], bundleType: String, version: String): Option[PacBioDataBundle] =
    getBundlesByType(bundles, bundleType).find(_.version == version)

}

object BundleUtils extends BundleUtils


class PacBioBundleDao(bundles: Seq[PacBioDataBundleIO] = Seq.empty[PacBioDataBundleIO]) extends BundleConstants{

  private var loadedBundles = mutable.ArrayBuffer.empty[PacBioDataBundleIO]

  bundles.foreach(b => loadedBundles += b)

  def getBundles = loadedBundles.map(_.bundle).toList

  def getBundlesByType(bundleType: String): Seq[PacBioDataBundle] =
    BundleUtils.getBundlesByType(loadedBundles.map(_.bundle), bundleType)

  def getNewestBundleVersionByType(bundleType: String): Option[PacBioDataBundle] =
    BundleUtils.getNewestBundleVersionByType(getBundles, bundleType)

  def getActiveBundleVersionByType(bundleType: String): Option[PacBioDataBundle] =
    getBundles.find(_.isActive)

  def getActiveBundleByType(bundleType: String): Option[PacBioDataBundle] =
    getBundles.filter(_.typeId == bundleType).find(_.isActive == true)

  def getActiveBundleIOByType(bundleType: String): Option[PacBioDataBundleIO] =
    loadedBundles.filter(_.bundle.typeId == bundleType).find(_.bundle.isActive == true)

  def getBundle(bundleType: String, version: String): Option[PacBioDataBundle] =
    BundleUtils.getBundlesByType(getBundles, bundleType).find(_.version == version)

  def getBundleIO(bundleType: String, version: String): Option[PacBioDataBundleIO] =
    loadedBundles.find(b => (b.bundle.version == version) && (b.bundle.typeId == bundleType))

  def addBundle(bundle: PacBioDataBundleIO): PacBioDataBundleIO = {
    loadedBundles += bundle
    bundle
  }

  /**
    * Get the newest upgrade possible for a given bundle type.
    *
    * @param bundleType
    * @return
    */
  def getBundleUpgrade(bundleType: String): PacBioDataBundleUpgrade = {
    implicit val orderBy = PacBioDataBundle.orderByBundleVersion

    val activeBundle = getActiveBundleByType(bundleType)

    val bundles = loadedBundles.map(_.bundle)
        .filter(_.typeId == bundleType)
        .filter(_.isActive == false)

    val opt = activeBundle match {
      case Some(acBundle) =>
        bundles.filter(b => b.version > acBundle.version).sorted.reverse.headOption
      case _ => None
    }

    PacBioDataBundleUpgrade(opt)
  }

  private def updateActiveSymLink(rootBundleDir: Path, bundleTypeId: String, bundlePath: Path): Path = {
    val px = rootBundleDir.resolve(s"$bundleTypeId-$ACTIVE_SUFFIX")
    if (Files.exists(px)) {
      px.toFile.delete()
    }
    Files.createSymbolicLink(px, bundlePath)
    px
  }

  private def setBundleAsActive(bundleType: String, bundleVersion: String): Try[PacBioDataBundleIO] = {
    val errorMessage = s"Unable to find $bundleType with version $bundleVersion"
    val newBundles = loadedBundles.map {b =>
      val bx = if (b.bundle.typeId == bundleType) {
        if (b.bundle.version == bundleVersion) b.bundle.copy(isActive = true)
        else b.bundle.copy(isActive = false)
      } else {
        b.bundle
      }
      b.copy(bundle = bx)
    }
    loadedBundles = newBundles

    getActiveBundleIOByType(bundleType)
        .map(b => Success(b))
        .getOrElse(Failure(new ResourceNotFoundError(errorMessage)))
  }

  /**
    * Mark the bundle as "Active" and create a symlink to the new active bundle with {bundle-type}-latest
    *
    * 1. Check if bundle is already active. Return bundle if so
    * 2.
    *
    * FIXME. This needs to be converted to {bundle-type}-active
    *
    * "latest" makes no sense.
    *
    * @param bundleType
    * @param bundleVersion
    * @return
    */
  def upgradeBundle(rootBundleDir: Path, bundleType: String, bundleVersion: String): Try[PacBioDataBundleIO] = {
    val errorMessage = s"Unable to find $bundleType with version $bundleVersion"

    getBundleIO(bundleType, bundleVersion).map { b =>

      val tx = for {
        px <- Try { updateActiveSymLink(rootBundleDir, b.bundle.typeId, b.path) }
        b <- setBundleAsActive(bundleType, bundleVersion)
        } yield b

      tx.recoverWith { case NonFatal(ex) => Failure(new UnprocessableEntityError(errorMessage + s" ${ex.getMessage}")) }
    }.getOrElse(Failure(new ResourceNotFoundError(s"Unable to find $bundleType with version $bundleVersion")))
  }
}


class PacBioBundleService(dao: PacBioBundleDao, rootBundle: Path)(implicit val actorSystem: ActorSystem) extends SmrtLinkBaseMicroService
    with DaoFutureUtils
    with BundleUtils
    with FileAndResourceDirectives{

  import SmrtLinkJsonProtocols._

  // for getFromFile to work
  implicit val routing = RoutingSettings.default


  val ROUTE_PREFIX = "bundles"

  // When the system is downloading bundles, they'll be placed temporarily here, the moved to
  // under the bundle root if they are valid
  val tmpRootBundleDir = Files.createTempDirectory("tmp-bundles")

  val manifest = PacBioComponentManifest(
    toServiceId("pacbio_bundles"),
    "PacBio Bundle Service",
    "0.1.0", "PacBio Service for registering Bundles of config files for extending the SL Core system")

  // Create a more terse error message
  def fromTry[T](errorMessage: String, t: Try[T]): Future[T] = {
    t match {
      case Success(r) => Future.successful(r)
      case Failure(ex) =>
        Future.failed(throw new UnprocessableEntityError(s"$errorMessage ${ex.getMessage}"))
    }
  }

  def getBundleByTypeAndVersion(bundleTypeId: String, bundleVersion: String): Future[PacBioDataBundleIO] =
    Future { dao.getBundleIO(bundleTypeId, bundleVersion) }
        .flatMap(failIfNone(s"Unable to find Bundle Type '$bundleTypeId' and version '$bundleVersion'"))

  val routes = {
    pathPrefix(ROUTE_PREFIX) {
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              dao.getBundles
            }
          }
        } ~
        post {
          entity(as[PacBioBundleRecord]) { record =>
            complete {
              created {
                for {
                  pathBundle <- fromTry[(Path, PacBioDataBundle)](s"Failed to process $record", Try { downloadAndParseBundle(record.url, tmpRootBundleDir) } )
                  validBundle <- fromTry[PacBioDataBundleIO](s"Bundle Already exists.", Try { copyBundleTo(pathBundle._1, pathBundle._2, rootBundle)})
                  addedBundle <- Future {dao.addBundle(validBundle)}
                } yield addedBundle.bundle
              }
            }
          }
        }
      } ~
      path(Segment) { bundleTypeId =>
        get {
          complete {
            ok {
              dao.getBundlesByType(bundleTypeId)
            }
          }
        }
      } ~
      path(Segment / "latest") { bundleTypeId =>
        get {
          complete {
            ok {
              Future {
                dao.getNewestBundleVersionByType(bundleTypeId)
              }.flatMap(failIfNone(s"Unable to find Newest Data Bundle for type '$bundleTypeId'"))
            }
          }
        }
      } ~
      path(Segment / "active") { bundleTypeId =>
        get {
          complete {
            ok {
              Future {
                dao.getActiveBundleVersionByType(bundleTypeId)
              }.flatMap(failIfNone(s"Unable to find Active Data Bundle for type '$bundleTypeId'"))
            }
          }
        }
      } ~
      path(Segment / "upgrade") { bundleTypeId =>
        get {
          complete {
            ok {
              Future {
                dao.getBundleUpgrade(bundleTypeId)
              }
            }
          }
        }
      } ~
      path(Segment / Segment / "upgrade") { (bundleTypeId, bundleVersion) =>
        post {
          complete {
            ok {
              for {
                b <- getBundleByTypeAndVersion(bundleTypeId, bundleVersion)
                bio <- fromTry[PacBioDataBundleIO](s"Unable to upgrade bundle $bundleTypeId and version $bundleVersion", dao.upgradeBundle(rootBundle, b.bundle.typeId, b.bundle.version))
              } yield bio.bundle
            }
          }
        }
      } ~
      path(Segment / Segment / "download") { (bundleTypeId, bundleVersion) =>
        pathEndOrSingleSlash {
          get {
            onSuccess(getBundleByTypeAndVersion(bundleTypeId, bundleVersion)) { case b: PacBioDataBundleIO =>
              val fileName = s"$bundleTypeId-$bundleVersion.tar.gz"
              logger.info(s"Downloading bundle $b to $fileName")
              respondWithHeader(HttpHeaders.`Content-Disposition`("attachment; filename=" + fileName)) {
                getFromFile(b.tarGzPath.toFile)
              }
            }
          }
        }
      } ~
      path(Segment / Segment) { (bundleTypeId, bundleVersion) =>
        get {
          complete {
            ok {
              getBundleByTypeAndVersion(bundleTypeId, bundleVersion).map(_.bundle)
            }
          }
        }
      }
    }
  }
}

trait PacBioBundleServiceProvider {
  this: SmrtLinkConfigProvider with ServiceComposer with ActorSystemProvider =>

  val pacBioBundleService: Singleton[PacBioBundleService] =
    Singleton { () =>
      implicit val system = actorSystem()
      new PacBioBundleService(new PacBioBundleDao(pacBioBundles()), pacBioBundleRoot())
    }

  addService(pacBioBundleService)
}


