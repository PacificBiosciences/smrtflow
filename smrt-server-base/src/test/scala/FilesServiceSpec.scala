import com.pacbio.common.dependency.Singleton
import com.pacbio.common.file.{JavaFileSystemUtil, FileSystemUtil}
import com.pacbio.common.services.CommonFilesServiceProvider
import com.pacbio.common.actors.InMemoryLogDaoProvider
import com.pacbio.common.app.{BaseApi, CoreProviders}
import com.pacbio.common.models.{DiskSpaceResource, PacBioJsonProtocol, DirectoryResource}
import org.specs2.mock._
import org.mockito.Mockito.doReturn

import org.specs2.mutable.Specification
import org.apache.commons.io.FileUtils

import spray.testkit.Specs2RouteTest
import spray.routing._
import spray.httpx.SprayJsonSupport._

import java.nio.file.{Path, Files, Paths}
import java.net.URLEncoder

class FilesServiceSpec extends Specification with Directives with Mockito with Specs2RouteTest {
  import PacBioJsonProtocol._

  val spiedFileSystemUtil = spy(new JavaFileSystemUtil)

  object Api extends BaseApi {
    override val providers: CoreProviders = new CoreProviders
      with InMemoryLogDaoProvider
      with CommonFilesServiceProvider {
        override val fileSystemUtil: Singleton[FileSystemUtil] = Singleton(() => spiedFileSystemUtil)
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
      val url = "/smrt-base/files" + tmpDir.toString
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
        res.totalSpace must beEqualTo(100)
        res.freeSpace must beEqualTo(50)
      }
    }
    "decode a path containing spaces" in {
      val tmpDir = Files.createTempDirectory("path with spaces")
      val tmpFile = tmpDir.resolve("data with spaces.txt").toFile
      FileUtils.writeStringToFile(tmpFile, "Hello, world!")
      val url = "/smrt-base/files/" + URLEncoder.encode(tmpDir.toString, "UTF-8")
      Get(url) ~> routes ~> check {
        val dirRes = responseAs[DirectoryResource]
        dirRes.fullPath must beEqualTo(tmpDir.toString)
        Files.exists(Paths.get(dirRes.fullPath)) must beTrue
        dirRes.files.head.name must beEqualTo("data with spaces.txt")
      }
    }
  }
}