
import com.pacbio.common.services.CommonFilesServiceProvider
import com.pacbio.common.actors.InMemoryLogDaoProvider
import com.pacbio.common.app.{BaseApi, CoreProviders}
import com.pacbio.common.models.{PacBioJsonProtocol, DirectoryResource}

import org.specs2.mutable.Specification
import org.apache.commons.io.FileUtils

import spray.testkit.Specs2RouteTest
import spray.routing._
import spray.httpx.SprayJsonSupport._

import java.nio.file.{Files, Paths}
import java.net.URLEncoder

class FilesServiceSpec extends Specification with Directives with Specs2RouteTest {
  import PacBioJsonProtocol._

  object Api extends BaseApi {
    override val providers: CoreProviders = new CoreProviders with InMemoryLogDaoProvider with CommonFilesServiceProvider {}
  }

  val routes = Api.routes

  "Files service" should {
    "return a DirectoryResource for '/'" in {
      Get("/smrt-base/files/") ~> routes ~> check {
        val dirRes = responseAs[DirectoryResource]
        dirRes.fullPath must beEqualTo("/")
      }
    }
    "include a FileResource for a new temporary file" in {
      //FIXME, these two tests are identical except for the file name.
      val fileName = "data.txt"
      val tmpDir = Files.createTempDirectory("files-test")
      val tmpFile = tmpDir.resolve(fileName).toFile
      FileUtils.writeStringToFile(tmpFile, "Hello, world!")
      val url = "/smrt-base/files" + URLEncoder.encode(tmpDir.toString(), "UTF-8")
      Get(url) ~> routes ~> check {
        val dirRes = responseAs[DirectoryResource]
        dirRes.files.size must beEqualTo(1)
        dirRes.files.headOption.map(_.name) must beSome(fileName)
      }
    }
    "decode a path containing spaces" in {
      val fileName = "data with spaces.txt"
      val tmpDir = Files.createTempDirectory("path with spaces")
      val tmpFile = tmpDir.resolve(fileName).toFile
      FileUtils.writeStringToFile(tmpFile, "Hello, world!")
      val url = "/smrt-base/files" + URLEncoder.encode(tmpDir.toString(), "UTF-8")
      Get(url) ~> routes ~> check {
        val dirRes = responseAs[DirectoryResource]
        dirRes.fullPath must beEqualTo(tmpDir.toString())
        Files.exists(Paths.get(dirRes.fullPath)) must beTrue
        dirRes.files.headOption.map(_.name) must beSome(fileName)
      }
    }
  }
}
