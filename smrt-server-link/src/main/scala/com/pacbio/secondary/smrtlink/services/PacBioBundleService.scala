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
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._

import scala.util.{Failure, Success, Try}
import org.joda.time.{DateTime => JodaDateTime}
import org.eclipse.jgit.api.Git

import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.semver.SemVersion
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.common.services._
import com.pacbio.common.utils.TarGzUtil
import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils


trait BundleUtils extends LazyLogging{

  val FILE = "definitions/PacBioAutomationConstraints.xml"
  val MANIFEST_FILE = "manifest.xml"

  /**
    * Parse the Bundle XML and return a PacBioBundle.
    *
    * The manifest.xml *MUST* be in the root directory of the bundle.
    *
    * @param file XML Manifest File
    * @return
    */
  def parseBundleManifestXml(file: File): PacBioBundle = {
    val xs = XML.loadFile(file)

    val bundleTypeId = (xs \ "Package").text
    val rawVersion = (xs \ "Version").text
    val author = (xs \ "Author").headOption.map(_.text)
    //val createdAt = (xs \ "Created").text

    PacBioBundle(bundleTypeId, rawVersion, JodaDateTime.now(), Paths.get(file.getParent), author)
  }

  def parseBundle(rootDir: Path): PacBioBundle =
    parseBundleManifestXml(rootDir.resolve(MANIFEST_FILE).toFile)

  def copyFile(file: File, outputDir: File): Path = {
    val tmpFile = File.createTempFile("pb-bundle", ".tar.gz")
    FileUtils.copyFile(file, tmpFile)

    TarGzUtil.uncompressTarGZ(file, outputDir)

    val outputDirs = outputDir.list().map(x => outputDir.toPath.resolve(x))

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
    val tmpFile = File.createTempFile("pb-bundle", ".tar.gz")
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
    def isTarGz(sx: String) = sx.endsWith(".tar.gz") | sx.endsWith(".tgz")

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
  def downloadAndParseBundle(url: URL, outputDir: Path): PacBioBundle = {
    val b = parseBundle(downloadBundle(url, outputDir))
    logger.info(s"Processed bundle $b")
    b
  }

  /**
    * Copy a Bundle to the root directory. The bundle will be named within the root dir
    * with the form:
    *
    * {bundle-type-id}-{version}
    *
    * If the directory already exists, an exception will be raised.
    *
    * @param pacBioBundle
    * @param rootDir
    * @return
    */
  def copyBundleTo(pacBioBundle: PacBioBundle, rootDir: Path): PacBioBundle = {
    val name = s"${pacBioBundle.typeId}-${pacBioBundle.version}"
    val bundleDir = rootDir.resolve(name)
    if (Files.exists(bundleDir)) {
      throw new IOException(s"Bundle $name already exists.")
    } else {
      FileUtils.copyDirectory(pacBioBundle.path.toFile, bundleDir.toFile)
    }
    pacBioBundle.copy(path = bundleDir)
  }

  def getManifestXmlFromDir(path: Path):Option[Path] =
    path.toFile.list()
        .find(_ == MANIFEST_FILE)
        .map(x => path.resolve(x))


  /**
    * Load bundles from a root directory. Each bundle must adhere to the spec and
    * have a single manifest.xml file in the bundle directory.
    *
    * root-dir
    *  - bundle-01/manifest.xml
    *  - bundle-02/manifest.xml
    *
    * @param path
    * @return
    */
  def loadBundlesFromRoot(path: Path): Seq[PacBioBundle] = {

    logger.info(s"Attempting to load bundles from $path")

    def getBundle(p: Path): Option[PacBioBundle] =
      getManifestXmlFromDir(p)
        .map(px => parseBundleManifestXml(px.toFile))

    // .list() can return null if there's a security issue
    val bundles = path.toAbsolutePath.toFile.list()
        .map(p => path.resolve(p))
        .flatMap(getBundle)

    logger.info(s"Successfully loaded ${bundles.length} PacBio Bundles.")
    bundles
  }


  def getBundlesByType(bundles: Seq[PacBioBundle], bundleType: String): Seq[PacBioBundle] =
    bundles.filter(_.typeId == bundleType)

  /**
    * Return the newest version of bundle as defined by SemVer
    * @param bundleType
    * @return
    */
  def getNewestBundleVersionByType(bundles: Seq[PacBioBundle], bundleType: String): Option[PacBioBundle] = {
    implicit val orderBy = PacBioBundle.orderByBundleVersion
    getBundlesByType(bundles, bundleType).sorted.reverse.headOption
  }

  def getBundle(bundles: Seq[PacBioBundle], bundleType: String, version: String): Option[PacBioBundle] =
    getBundlesByType(bundles, bundleType).find(_.version == version)

}

object BundleUtils extends BundleUtils


class PacBioBundleDao(bundles: Seq[PacBioBundle] = Seq.empty[PacBioBundle]) {

  private var loadedBundles = mutable.ArrayBuffer.empty[PacBioBundle]

  bundles.foreach(b => loadedBundles += b)

  def getBundles = loadedBundles.toList

  def getBundlesByType(bundleType: String): Seq[PacBioBundle] =
    BundleUtils.getBundlesByType(loadedBundles, bundleType)

  def getNewestBundleVersionByType(bundleType: String): Option[PacBioBundle] =
    BundleUtils.getNewestBundleVersionByType(loadedBundles, bundleType)

  def getBundle(bundleType: String, version: String): Option[PacBioBundle] =
    BundleUtils.getBundlesByType(loadedBundles, bundleType).find(_.version == version)

  def addBundle(bundle: PacBioBundle): PacBioBundle = {
    loadedBundles += bundle
    bundle
  }

}


class PacBioBundleService(dao: PacBioBundleDao, rootBundle: Path) extends SmrtLinkBaseMicroService
    with DaoFutureUtils
    with BundleUtils{

  import SmrtLinkJsonProtocols._

  val ROUTE_PREFIX = "bundles"

  // When the system is downloading bundles, they'll be placed temporarily here, the moved to
  // under the bundle root if they are valid
  val tmpBundleDir = Files.createTempDirectory("tmp-bundles")

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
                  bundle <- fromTry[PacBioBundle](s"Failed to process $record", Try { downloadAndParseBundle(record.url, tmpBundleDir) })
                  validBundle <- fromTry[PacBioBundle](s"Bundle Already exists.", Try { copyBundleTo(bundle, rootBundle)})
                  addedBundle <- Future {dao.addBundle(validBundle)}
                } yield addedBundle
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
              }.flatMap(failIfNone(s"Unable to find Newest Bundle for type '$bundleTypeId'"))
            }
          }
        }
      } ~
      path(Segment / Segment) { (bundleTypeId, bundleVersion) =>
        get {
          complete {
            ok {
              Future {
                dao.getBundle(bundleTypeId, bundleVersion)
              }.flatMap(failIfNone(s"Unable to find Bundle Type '$bundleTypeId' and version '$bundleVersion'"))
            }
          }
        }
      }
    }
  }
}

trait PacBioBundleServiceProvider {
  this: SmrtLinkConfigProvider with ServiceComposer =>

  val pacBioBundleService: Singleton[PacBioBundleService] =
    Singleton(() => new PacBioBundleService(new PacBioBundleDao(pacBioBundles()), pacBioBundleRoot()))

  addService(pacBioBundleService)
}


