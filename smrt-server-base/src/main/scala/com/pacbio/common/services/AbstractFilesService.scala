package com.pacbio.common.services

import java.io.File
import java.net.URLDecoder
import java.nio.file.{Paths, Path}

import akka.actor.ActorSystem
import akka.util.Timeout
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import spray.http.Uri
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.RoutingSettings

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

abstract class AbstractFilesService(mimeTypes: MimeTypes)(
    implicit val actorSystem: ActorSystem,
    implicit val ec: ExecutionContext)
  extends PacBioService {

  import PacBioJsonProtocol._
  import SprayJsonSupport._

  implicit val timeout = Timeout(10.seconds)
  implicit val routing = RoutingSettings.default

  //////// SUB-CLASSING INTERFACE ////////
  val serviceBaseId: String
  val serviceName: String
  val serviceVersion: String
  val serviceDescription: String

  /**
   * Resolves an absolute path from the URI of the incoming request to an absolute path to a file
   * or directory on the filesystem.
   */
  def resolvePath(path: Path): Future[Option[Path]]
  ////////////////////////////////////////

  private def resolve(path: Uri.Path): Future[File] = {
    val realPath = java.net.URLDecoder.decode(path.toString(), "UTF-8")
    val absPath = Paths.get("/").resolve(Paths.get(realPath))
    resolvePath(absPath).map {
      case Some(p) => p.toFile
      case None => throw new ResourceNotFoundError(s"Unable to resolve path: ${path.toString()}")
    }
  }

  // manifest and routes must be declared lazy, because scala initializes superclasses first, and
  // the serviceBaseId, et. al. will be null

  override lazy val manifest = PacBioComponentManifest(
    toServiceId(serviceBaseId),
    serviceName,
    serviceVersion,
    serviceDescription)

  // TODO(smcclellan): Add auth
  override lazy val routes =
    pathPrefix(serviceBaseId) {
      path(RestPath) { path =>
        get {
          complete {
            ok {
              resolve(path).map(getResource)
            }
          }
        }
      }
    } ~
    pathPrefix(s"$serviceBaseId-download") {
      path(RestPath) { path =>
        onSuccess(resolve(path)) { file =>
          getFromFile(file)
        }
      }
    }

  private def getResource(file: File): DirectoryResource = {
    def sizeReadable(sizeInBytes: Long): String = {
      if (sizeInBytes < 1024) return s"$sizeInBytes B"

      // Size units have a distance of 10 bits (1024=2^10) meaning the position of the highest
      // non-zero bit - or in other words the number of leading zeros - differ by 10. This looks a
      // little hacky but it works and it's fast.
      val s: Int = (63 - java.lang.Long.numberOfLeadingZeros(sizeInBytes)) / 10
      val scaleChar: Char = " KMGTPE".charAt(s)
      val sizeNum: Double = sizeInBytes.toDouble / (1L << (s*10))
      f"$sizeNum%.1f ${scaleChar}B"
    }

    def toFileResource(f: File): FileResource = FileResource(
        f.getAbsolutePath,
        f.getName,
        mimeTypes(f),
        f.length(),
        sizeReadable(f.length()))

    def toDirectoryResource(f: File): DirectoryResource = {
      val subDirectories: Seq[DirectoryResource] = f
        .listFiles()
        .toSeq
        .filter(_.isDirectory)
        // Only search one level deep, no recursion.
        .map(d => DirectoryResource(d.getAbsolutePath, subDirectories = Seq.empty, files = Seq.empty))

      val files: Seq[FileResource] = f
        .listFiles()
        .toSeq
        .filterNot(_.isDirectory)
        .map(toFileResource)

      DirectoryResource(f.getAbsolutePath, subDirectories, files)
    }

    if (file.exists())
      if (file.isDirectory)
        toDirectoryResource(file)
      else
        throw new ResourceNotFoundError(s"Expected directory but found file at path ${file.getAbsolutePath}")
    else
      throw new ResourceNotFoundError(s"No file or directory found at path ${file.getAbsolutePath}")
  }
}

/**
 * Resolves request paths against a single root directory. E.g., consider:
 *
 * {{{
 *   class FooFilesService(...) extends SimpleFilesService(...) {
 *     override val serviceBaseId = "foo-files"
 *     override val serviceName = "Foo Files Service"
 *     override val serviceVersion = "0.1.0"
 *     override val serviceDescription = "Static file service for directory /foo"
 *
 *     override val rootDir = Paths.get("/foo")
 *   }
 * }}}
 *
 * This would handle a call like {{{GET /foo-files/bar/dir}}} by returning a
 * {{{DirectoryResource}}} representing the directory {{{/foo/bar/dir}}}. And it would handle a
 * call like {{{GET /foo-files-download/bar/file.txt}}} by serving the contents of the file
 * located at {{{/foo/bar/file.txt}}}.
 */
abstract class SimpleFilesService(mimeTypes: MimeTypes)(
    override implicit val actorSystem: ActorSystem,
    override implicit val ec: ExecutionContext)
  extends AbstractFilesService(mimeTypes) {

  /**
   * The root directory against which incoming requests will be resolved. (Should be absolute.)
   */
  val rootDir: Path

  override def resolvePath(path: Path): Future[Option[Path]] = Future {
    Some(rootDir.resolve(Paths.get("/").relativize(path)))
  }
}

/**
 * Resolves request paths against a view mapping. E.g., consider:
 *
 * {{{
 *   class FooFilesService(...) extends ViewFilesService(...) {
 *     override val serviceBaseId = "foo-files"
 *     override val serviceName = "Foo Files Service"
 *     override val serviceVersion = "0.1.0"
 *     override val serviceDescription = "Static file service for directory /foo"
 *
 *     override val view = Map(
 *       Paths.get("/bar-resources") -> Paths.get("/foo/bar/path/to/resources")
 *       Paths.get("/baz-resources") -> Paths.get("/foo/baz/path/to/resources")
 *     )
 *   }
 * }}}
 *
 * This would handle a call like {{{GET /foo-files/bar-resources/dir}}} by returning a
 * {{{DirectoryResource}}} representing the directory {{{/foo/bar/path/to/resources/dir}}}. And it
 * would handle a call like {{{GET /foo-files-download/baz-resources/file.txt}}} by serving the
 * contents of the file located at {{{/foo/baz/path/to/resources/file.txt}}}.
 */
abstract class ViewFilesService(mimeTypes: MimeTypes)(
    override implicit val actorSystem: ActorSystem,
    override implicit val ec: ExecutionContext)
  extends AbstractFilesService(mimeTypes) {

  /**
   * The view, mapping request URI path prefixes to filesystem prefixes. (Both the URI path
   * prefixes and the filesystem prefixes should be absolute.)
   */
  val view: Map[Path, Path]

  require(
    view.forall(v => v._1.isAbsolute && v._2.isAbsolute),
    "ViewFilesService requires all view paths to be absolute")

  override def resolvePath(path: Path): Future[Option[Path]] = Future {
    view.find(v => path.startsWith(v._1)).map(v => v._2.resolve(v._1.relativize(path)))
  }
}
