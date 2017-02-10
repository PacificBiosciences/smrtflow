package com.pacbio.secondary.smrtlink.services

import java.io.File
import java.net.URL
import java.nio.file.{Path, Paths}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent._
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._
import scala.util.Try

import org.joda.time.{DateTime => JodaDateTime}
import org.eclipse.jgit.api.Git
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services._
import com.pacbio.secondary.analysis.jobs.UrlProtocol
import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.loaders.PacBioAutomationConstraintsLoader
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols


object BundleModels {

  case class PacBioBundle(typeId: String,
                          version: String,
                          importedAt: JodaDateTime,
                          path: Path)

  // Use to create a bundle record
  case class PacBioBundleRecord(url: URL)

}

trait BundleModelsJsonProtocol extends SmrtLinkJsonProtocols with UrlProtocol{

  import BundleModels._

  implicit val pacbioBundleFormat = jsonFormat4(PacBioBundle)
  implicit val pacbioBundleRecordFormat = jsonFormat1(PacBioBundleRecord)
}

object BundleModelsJsonProtocol extends BundleModelsJsonProtocol

trait BundleUtils {

  import BundleModels._

  val FILE = "definitions/PacBioAutomationConstraints.xml"

  /**
    * Parse the Bundle XML and return a PacBioBundle
    *
    * @param path Path to bundle XML
    * @return
    */
  def parseBundleXml(path: Path): PacBioBundle = {
    PacBioBundle("chemistry", "1.0.0", JodaDateTime.now(), Paths.get("/tmp"))
  }

  def parseBundle(rootDir: Path): PacBioBundle =
    parseBundleXml(rootDir.resolve(FILE))

  def downloadFile(file: File): Path = file.toPath

  def downloadHttp(url: URL): Path = Paths.get("/my-http-downloaded-bundle")

  def downloadGit(url: URL): Path = {
    val localPath = Paths.get("/tmp")

    val result = Git.cloneRepository()
        .setURI(url.toURI.toString)
        .setDirectory(localPath.toFile)
        .call()

    println(s"Result $result")
    Paths.get("/tmp")
  }

  /**
    * This will download the bundle and return the path to
    * the output directory
    *
    * @param url       URL of
    * @param outputDir Root Output Dir
    * @return
    */
  def downloadBundle(url: URL, outputDir: Path): Path = {

    def isGit(sx: String) = sx.endsWith(".git")

    Option(url.getProtocol) match {
      case Some("http") if isGit(url.getFile) => downloadGit(url)
      case Some("http") => downloadHttp(url)
      case Some("file") => downloadFile(Paths.get(url.toURI).toFile)
      case None => downloadFile(Paths.get(url.toURI).toFile)
      case Some(x) => println(s"Unsupported protocol '$x'")
    }

    outputDir
  }

  def downloadAndParseBundle(url: URL, outputDir: Path): PacBioBundle =
    parseBundle(downloadBundle(url, outputDir))

}

class PacBioBundleDao(bundles: Seq[BundleModels.PacBioBundle] = Seq.empty[BundleModels.PacBioBundle]) {

  private var loadedBundles = bundles

  def getBundles = loadedBundles

  def getBundlesByType(bundleType: String): Seq[BundleModels.PacBioBundle] =
    loadedBundles.filter(_.typeId == bundleType)

  /**
    * This needs to support sorting by SemVer string
    * @param bundleType
    * @return
    */
  def getNewestBundleByType(bundleType: String): Option[BundleModels.PacBioBundle] =
    getBundlesByType(bundleType).headOption

  def getBundle(bundleType: String, version: String): Option[BundleModels.PacBioBundle] =
    getBundlesByType(bundleType).find(_.version == version)


}
object BundleUtils extends BundleUtils


/**
  * Created by mkocher on 2/8/17.
  */
class PacBioBundleService(dao: PacBioBundleDao) extends SmrtLinkBaseMicroService
    with DaoFutureUtils
    with BundleUtils{

  import BundleModels._
  import BundleModelsJsonProtocol._

  val ROUTE_PREFIX = "bundles"

  val manifest = PacBioComponentManifest(
    toServiceId("pacbio_bundles"),
    "PacBio Bundle Service",
    "0.1.0", "PacBio Service for registering Bundles of config files for extending the SL Core system")


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
               Future.fromTry(Try { downloadAndParseBundle(record.url, Paths.get("/tmp/bundles")) })
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
                dao.getNewestBundleByType(bundleTypeId)
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

  val now = JodaDateTime.now()

  val ROOT_BUNDLE_DIR = Paths.get("/tmp/bundles")

  val bundleDatum = Seq(
    ("chemistry", "1.0.0"),
    ("chemistry", "1.2.3"),
    ("chemistry", "1.2.3"),
    ("pbpipelines", "1.0.0"))

  val exampleBundles = bundleDatum.map(x => BundleModels.PacBioBundle(x._1, x._2, now, ROOT_BUNDLE_DIR.resolve(s"bundle-${x._2}")))

  val pacBioBundleService: Singleton[PacBioBundleService] = Singleton(() => new PacBioBundleService(new PacBioBundleDao(exampleBundles)))

  addService(pacBioBundleService)
}


