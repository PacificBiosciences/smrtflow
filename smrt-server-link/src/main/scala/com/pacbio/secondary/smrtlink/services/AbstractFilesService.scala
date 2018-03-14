package com.pacbio.secondary.smrtlink.services

import java.io.File
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.util.Timeout
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.smrtlink.file.FileSystemUtil
import com.pacbio.secondary.smrtlink.models.MimeTypes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.PathMatchers.RemainingPath // Was this RestPath from spray?

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

// TODO(smcclellan): Is this abstraction necessary? We initially planned to have other file services.
abstract class AbstractFilesService(mimeTypes: MimeTypes,
                                    fileSystemUtil: FileSystemUtil)(
    implicit val actorSystem: ActorSystem,
    implicit val ec: ExecutionContext)
    extends PacBioService {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
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

  private def resolve(path: Uri.Path): Future[Path] = {
    val realPath = java.net.URLDecoder.decode(path.toString(), "UTF-8")
    val absPath = Paths.get("/").resolve(Paths.get(realPath))
    resolvePath(absPath).map {
      case Some(p) => p
      case None =>
        throw new ResourceNotFoundError(
          s"Unable to resolve path: ${path.toString()}")
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
      path(RemainingPath) { path =>
        get {
          complete {
            resolve(path).map(getDirectoryResource)
          }
        }
      }
    } ~
      pathPrefix(s"$serviceBaseId-diskspace") {
        path(RemainingPath) { path =>
          get {
            complete {
              resolve(path).map(getDiskSpaceResource)
            }
          }
        }
      }

  private def getDirectoryResource(path: Path): DirectoryResource = {
    def sizeReadable(sizeInBytes: Long): String = {
      if (sizeInBytes < 1024) return s"$sizeInBytes B"

      // Size units have a distance of 10 bits (1024=2^10) meaning the position of the highest
      // non-zero bit - or in other words the number of leading zeros - differ by 10. This looks a
      // little hacky but it works and it's fast.
      val s: Int = (63 - java.lang.Long.numberOfLeadingZeros(sizeInBytes)) / 10
      val scaleChar: Char = " KMGTPE".charAt(s)
      val sizeNum: Double = sizeInBytes.toDouble / (1L << (s * 10))
      f"$sizeNum%.1f ${scaleChar}B"
    }

    def toFileResource(f: File): FileResource =
      FileResource(f.getAbsolutePath,
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
        .map(
          d =>
            DirectoryResource(d.getAbsolutePath,
                              subDirectories = Seq.empty,
                              files = Seq.empty))

      val files: Seq[FileResource] = f
        .listFiles()
        .toSeq
        .filterNot(_.isDirectory)
        .map(toFileResource)

      DirectoryResource(f.getAbsolutePath, subDirectories, files)
    }

    val file = fileSystemUtil.getFile(path)

    if (file.exists())
      if (file.isDirectory)
        toDirectoryResource(file)
      else
        throw new ResourceNotFoundError(
          s"Expected directory but found file at path ${file.getAbsolutePath}")
    else
      throw new ResourceNotFoundError(
        s"No file or directory found at path ${file.getAbsolutePath}")
  }

  private def getDiskSpaceResource(path: Path): DiskSpaceResource =
    DiskSpaceResource(path.toString,
                      fileSystemUtil.getTotalSpace(path),
                      fileSystemUtil.getFreeSpace(path))
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
abstract class SimpleFilesService(mimeTypes: MimeTypes,
                                  fileSystemUtil: FileSystemUtil)(
    override implicit val actorSystem: ActorSystem,
    override implicit val ec: ExecutionContext)
    extends AbstractFilesService(mimeTypes, fileSystemUtil) {

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
abstract class ViewFilesService(mimeTypes: MimeTypes,
                                fileSystemUtil: FileSystemUtil)(
    override implicit val actorSystem: ActorSystem,
    override implicit val ec: ExecutionContext)
    extends AbstractFilesService(mimeTypes, fileSystemUtil) {

  /**
    * The view, mapping request URI path prefixes to filesystem prefixes. (Both the URI path
    * prefixes and the filesystem prefixes should be absolute.)
    */
  val view: Map[Path, Path]

  require(view.forall(v => v._1.isAbsolute && v._2.isAbsolute),
          "ViewFilesService requires all view paths to be absolute")

  override def resolvePath(path: Path): Future[Option[Path]] = Future {
    view
      .find(v => path.startsWith(v._1))
      .map(v => v._2.resolve(v._1.relativize(path)))
  }
}
