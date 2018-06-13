import java.io.File

import org.specs2.mutable.Specification
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.actor.{ActorRefFactory, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.Specs2RouteTest
import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.secondary.smrtlink.dependency.{
  ConfigProvider,
  SetBindings,
  Singleton
}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  EngineCoreConfigLoader,
  PbsmrtpipeConfigLoader
}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.actors.{
  ActorRefFactoryProvider,
  SmrtLinkDalProvider,
  _
}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestUtils}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import slick.driver.PostgresDriver.api.Database
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.{FileIO, Source}
import com.pacbio.secondary.smrtlink.analysis.bio.FastaIterator
import com.pacbio.secondary.smrtlink.auth.JwtUtils.JWT_HEADER
import com.typesafe.config.Config

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

  object TestProviders
      extends ServiceComposer
      with SmrtLinkConfigProvider
      with ProjectServiceProvider
      with SmrtLinkTestDalProvider
      with ConfigProvider
      with ActorSystemProvider
      with PbsmrtpipeConfigLoader
      with EngineCoreConfigLoader
      with UploadFileServiceProvider
      with DataSetServiceProvider
      with EventManagerActorProvider
      with JobsDaoProvider
      with JwtUtilsProvider
      with FakeClockProvider
      with SetBindings {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() =>
      new JwtUtils {
        override def parse(jwt: String): Option[UserRecord] =
          if (jwt == INVALID_JWT) None else Some(UserRecord(jwt))
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorSystem: Singleton[ActorSystem] = Singleton(system)
    override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.uploadFileService().prefixedRoutes
  step(setupDb(TestProviders.dbConfig))

  "Upload File service" should {
    "upload a fasta file" in {
      val numRecords = 1000
      val fastaPath = MockFileUtils.writeMockTmpFastaFile(numRecords)
      val chunkSize = 100000
      val fx = fastaPath.toFile
      val formData =
        Multipart.FormData(
          Source.single(Multipart.FormData.BodyPart(
            "upload_file",
            HttpEntity(ContentTypes.`application/octet-stream`,
                       fx.length(),
                       FileIO.fromPath(fastaPath, chunkSize = chunkSize)),
            Map("filename" -> fx.getName)
          )))

      Post("/smrt-link/uploader", formData) ~> addHeader(READ_CREDENTIALS) ~> totalRoutes ~> check {
        status shouldEqual StatusCodes.Created
        val fileUploadResponse = responseAs[FileUploadResponse]
        val f1 = fileUploadResponse.path.toFile
        f1.exists must beTrue

        val reader = new FastaIterator(f1)
        reader.isEmpty must beFalse
        reader.length === numRecords
      }
    }
  }

}
