import java.nio.file.{Path, Paths}

import com.pacbio.common.dependency.{Singleton, StringConfigProvider}
import com.pacbio.common.file.{FileSystemUtil, FileSystemUtilProvider, JavaFileSystemUtil}
import com.pacbio.common.models.{DiskSpaceResource, PacBioJsonProtocol}
import com.pacbio.common.services.{DiskSpaceServiceProviderx, ServiceComposer}
import org.mockito.Mockito._
import org.specs2.mock._
import org.specs2.mutable.Specification
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

class DiskSpaceServiceSpec extends Specification with Directives with Mockito with Specs2RouteTest {
  import PacBioJsonProtocol._

  val spiedFileSystemUtil = spy(new JavaFileSystemUtil)

  val TEST_JOB_DIR = "/test/job/dir"

  object TestProviders extends
    ServiceComposer with
    StringConfigProvider with
    DiskSpaceServiceProviderx with
    FileSystemUtilProvider {
      override val fileSystemUtil: Singleton[FileSystemUtil] = Singleton(() => spiedFileSystemUtil)
      override val configString = Singleton(() =>
        s"""
           |smrtflow {
           |  engine {
           |    jobRootDir = "$TEST_JOB_DIR"
           |  }
           |}
        """.stripMargin)
    }

  doReturn(100.toLong).when(spiedFileSystemUtil).getTotalSpace(Paths.get("/"))
  doReturn(50.toLong).when(spiedFileSystemUtil).getFreeSpace(Paths.get("/"))

  doReturn(200.toLong).when(spiedFileSystemUtil).getTotalSpace(Paths.get(TEST_JOB_DIR))
  doReturn(150.toLong).when(spiedFileSystemUtil).getFreeSpace(Paths.get(TEST_JOB_DIR))

  val routes = TestProviders.diskSpaceService().prefixedRoutes

  "Disk space service" should {
    "return a DiskSpaceResource for every id" in {
      Get("/smrt-base/disk-space") ~> routes ~> check {
        val res = responseAs[Seq[DiskSpaceResource]]
        res.length must beEqualTo(2)
        res.map(_.fullPath) must contain("/")
        res.map(_.fullPath) must contain(TEST_JOB_DIR)
      }
    }
    "return a DiskSpaceResource for the root dir" in {
      Get("/smrt-base/disk-space/smrtlink.resources.root") ~> routes ~> check {
        val res = responseAs[DiskSpaceResource]
        res.fullPath must beEqualTo("/")
        res.totalSpace must beEqualTo(100)
        res.freeSpace must beEqualTo(50)
      }
    }
    "return a DiskSpaceResource for the job root dir" in {
      Get("/smrt-base/disk-space/smrtlink.resources.jobs_root") ~> routes ~> check {
        val res = responseAs[DiskSpaceResource]
        res.fullPath must beEqualTo(TEST_JOB_DIR)
        res.totalSpace must beEqualTo(200)
        res.freeSpace must beEqualTo(150)
      }
    }
  }
}