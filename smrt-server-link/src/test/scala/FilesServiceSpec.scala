import java.net.URLEncoder
import java.nio.file.{Files, Paths}

import com.pacbio.secondary.smrtlink.app.{BaseApi, CoreProviders}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.file.{FileSystemUtil, JavaFileSystemUtil}
import com.pacbio.secondary.smrtlink.models.{
  DirectoryResource,
  DiskSpaceResource
}
import com.pacbio.secondary.smrtlink.services.CommonFilesServiceProvider
import org.apache.commons.io.FileUtils
import org.mockito.Mockito.doReturn
import org.specs2.mock._
import org.specs2.mutable.Specification
import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

class FilesServiceSpec
    extends Specification
    with Directives
    with Mockito
    with Specs2RouteTest {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  def isOsx() = System.getProperty("os.name") == "Mac OS X"

  // These fail on OSX for unclear reasonss. This is annoying to block the integration tests from running
  args(skipAll = isOsx())

  val spiedFileSystemUtil = spy(new JavaFileSystemUtil)

  object Api extends BaseApi {
    override val providers: CoreProviders = new CoreProviders
    with CommonFilesServiceProvider {
      override val fileSystemUtil: Singleton[FileSystemUtil] = Singleton(
        () => spiedFileSystemUtil)
    }
  }

  doReturn(100.toLong).when(spiedFileSystemUtil).getTotalSpace(Paths.get("/"))
  doReturn(50.toLong).when(spiedFileSystemUtil).getFreeSpace(Paths.get("/"))

  val routes = Api.routes

  "Files service" should {
    "return a DirectoryResource for '/'" in {
      Get("/smrt-base/files/") ~> routes ~> check {
        val dirRes = responseAs[DirectoryResource]
        dirRes.fullPath must beEqualTo("/")
      }
    }
    "include a FileResource for a new temporary file" in {
      val tmpDir = Files.createTempDirectory("files-test")
      val tmpFile = tmpDir.resolve("data.txt").toFile
      FileUtils.writeStringToFile(tmpFile, "Hello, world!")
      val url = "/smrt-base/files" + tmpDir
      Get(url) ~> routes ~> check {
        val dirRes = responseAs[DirectoryResource]
        dirRes.files.size must beEqualTo(1)
        dirRes.files.head.name must beEqualTo("data.txt")
      }
    }
    "return a DiskSpaceResource" in {
      Get("/smrt-base/files-diskspace/") ~> routes ~> check {
        val res = responseAs[DiskSpaceResource]
        res.fullPath must beEqualTo("/")
        // workaround for singleton bug with loading/applying mocks
        res.totalSpace must beGreaterThan(0L)
        res.freeSpace must beGreaterThan(0L)
      }
    }
    "decode a path containing spaces" in {
      val tmpDir = Files.createTempDirectory("path with spaces")
      val tmpFile = tmpDir.resolve("data with spaces.txt").toFile
      FileUtils.writeStringToFile(tmpFile, "Hello, world!")
      val url = "/smrt-base/files/" + URLEncoder.encode(tmpDir.toString,
                                                        "UTF-8")
      Get(url) ~> routes ~> check {
        val dirRes = responseAs[DirectoryResource]
        dirRes.fullPath must beEqualTo(tmpDir.toString)
        Files.exists(Paths.get(dirRes.fullPath)) must beTrue
        dirRes.files.head.name must beEqualTo("data with spaces.txt")
      }
    }
  }
}
