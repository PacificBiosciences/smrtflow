import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.dependency.{
  Singleton,
  StringConfigProvider
}
import com.pacbio.secondary.smrtlink.file.{
  FileSystemUtil,
  FileSystemUtilProvider,
  JavaFileSystemUtil
}
import com.pacbio.secondary.smrtlink.models.DiskSpaceResource
import com.pacbio.secondary.smrtlink.services.{
  DiskSpaceServiceProviderx,
  ServiceComposer
}
import org.mockito.Mockito._
import org.specs2.mock._
import org.specs2.mutable.Specification
import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.Specs2RouteTest

class DiskSpaceServiceSpec
    extends Specification
    with Directives
    with Mockito
    with Specs2RouteTest {

  val spiedFileSystemUtil = spy(new JavaFileSystemUtil)

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val TEST_JOB_DIR = "/test/job/dir"
  val TEST_TMP_DIR = "/test/tmp/dir"

  object TestProviders
      extends ServiceComposer
      with StringConfigProvider
      with DiskSpaceServiceProviderx
      with FileSystemUtilProvider {
    override val fileSystemUtil: Singleton[FileSystemUtil] = Singleton(
      () => spiedFileSystemUtil)
    override val configString = Singleton(() => s"""
           |smrtflow {
           |  engine {
           |    jobRootDir = "$TEST_JOB_DIR"
           |  }
           |}
           |
           |pacBioSystem {
           |  tmpDir = "$TEST_TMP_DIR"
           |}
        """.stripMargin)
  }

  doReturn(100.toLong).when(spiedFileSystemUtil).getTotalSpace(Paths.get("/"))
  doReturn(50.toLong).when(spiedFileSystemUtil).getFreeSpace(Paths.get("/"))

  doReturn(200.toLong)
    .when(spiedFileSystemUtil)
    .getTotalSpace(Paths.get(TEST_JOB_DIR))
  doReturn(150.toLong)
    .when(spiedFileSystemUtil)
    .getFreeSpace(Paths.get(TEST_JOB_DIR))

  doReturn(300.toLong)
    .when(spiedFileSystemUtil)
    .getTotalSpace(Paths.get(TEST_TMP_DIR))
  doReturn(250.toLong)
    .when(spiedFileSystemUtil)
    .getFreeSpace(Paths.get(TEST_TMP_DIR))

  val routes = TestProviders.diskSpaceService().prefixedRoutes

  "Disk space service" should {
    "return a DiskSpaceResource for every id" in {
      Get("/smrt-base/disk-space") ~> routes ~> check {
        val res = responseAs[Seq[DiskSpaceResource]]
        res.length must beEqualTo(3)
        res.map(_.fullPath) must contain("/")
        res.map(_.fullPath) must contain(TEST_JOB_DIR)
        res.map(_.fullPath) must contain(TEST_TMP_DIR)
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
    "return a DiskSpaceResource for the tmp dir" in {
      Get("/smrt-base/disk-space/smrtlink.resources.tmpdir") ~> routes ~> check {
        val res = responseAs[DiskSpaceResource]
        res.fullPath must beEqualTo(TEST_TMP_DIR)
        res.totalSpace must beEqualTo(300)
        res.freeSpace must beEqualTo(250)
      }
    }
  }
}
