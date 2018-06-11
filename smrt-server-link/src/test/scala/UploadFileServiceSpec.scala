import org.specs2.mutable.Specification
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.{ContentTypes, Multipart, StatusCodes}
import akka.http.scaladsl.testkit.Specs2RouteTest
import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.secondary.smrtlink.dependency.{SetBindings, Singleton}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.actors.{ActorRefFactoryProvider, SmrtLinkDalProvider, _}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestUtils}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import slick.driver.PostgresDriver.api.Database
import java.nio.file.{Files, Path}

import akka.http.scaladsl.model.headers.RawHeader
import com.pacbio.secondary.smrtlink.auth.JwtUtils.JWT_HEADER

/**
  * This spec has been updated to support multiple runs (i.e.,
  * not necessary to drop and create+migrate the db) This should be re-evaluated.
  *
  */
class UploadFileServiceSpec
  extends Specification
    with Specs2RouteTest
    with SetupMockData
    with PacBioServiceErrors
    with JobServiceConstants
    with SmrtLinkConstants
    with TestUtils {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  sequential

  val INVALID_JWT = "invalid.jwt"
  val READ_USER_LOGIN = "reader"
  val READ_CREDENTIALS = RawHeader(JWT_HEADER, READ_USER_LOGIN)
  val testSmrtLinkVersion = "1.2.3"

  trait TestUploadFileServiceProvider {
    this: ActorSystemProvider
      with SmrtLinkTestDalProvider
      with SmrtLinkConfigProvider
      with ServiceComposer =>

    val uploadFileService: Singleton[UploadFileService] =
      Singleton { () =>
        new UploadFileService(smrtLinkTempDir())(system)
      }

    addService(uploadFileService)
  }

  //  trait UploadFileServiceProvider {
  //    this: ActorSystemProvider with SmrtLinkConfigProvider with ServiceComposer =>
  //
  //    val uploadFileService: Singleton[UploadFileService] =
  //      Singleton { () =>
  //        implicit val system = actorSystem()
  //        new UploadFileService(smrtLinkTempDir())(system)
  //      }
  //
  //    addService(uploadFileService)
  //  }

  object TestProviders
    extends ServiceComposer
      with ProjectServiceProvider
      with SmrtLinkTestDalProvider
      with SmrtLinkConfigProvider
      with PbsmrtpipeConfigLoader
      with EngineCoreConfigLoader
      with TestUploadFileServiceProvider
      with DataSetServiceProvider
      with EventManagerActorProvider
      with JobsDaoProvider
      with JwtUtilsProvider
      with FakeClockProvider
      with SetBindings
      with ActorRefFactoryProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() =>
      new JwtUtils {
        override def parse(jwt: String): Option[UserRecord] =
          if (jwt == INVALID_JWT) None else Some(UserRecord(jwt))
      })

    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(
      system)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.uploadFileService().prefixedRoutes
  step(setupDb(TestProviders.dbConfig))

  class UploadResponse(path: String);

  "Upload File service" should {
    "upload a fasta file" in {
      val fastaPath: Path = MockFileUtils.writeMockTmpFastaFile()
      val fileData = Multipart.FormData.BodyPart.fromPath("mockFasta", ContentTypes.`text/plain(UTF-8)`, fastaPath)
      val formData = Multipart.FormData(fileData)
      Post("/smrt-link/uploader", formData)  ~> addHeader(READ_CREDENTIALS) ~> totalRoutes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[UploadResponse];
        // ... verify that response.path points to a valid file.
      }
    }
  }

}

