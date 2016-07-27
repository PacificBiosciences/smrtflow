package com.pacbio.secondary.lims.util

import java.lang.System.nanoTime
import java.util.UUID
import java.util.concurrent.Executors

import com.pacbio.secondary.lims.JsonProtocol._
import com.pacbio.secondary.lims.LimsSubreadSet
import com.pacbio.secondary.lims.services.{ImportLims, ResolveDataSet}
import org.specs2.mutable.Specification
import spray.http.{BodyPart, _}
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.testkit.Specs2RouteTest

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.duration.Duration


/**
 * Shared methods for creating and loading mock data
 *
 * This functionality is best exercised and demonstrated in the stress testing; however, it is
 * generally helpful for any case where mock data is needed for testing.
 *
 * See README.md#Tests testing for examples.
 */
trait StressUtil {
  this: Specification with Specs2RouteTest with ImportLims with ResolveDataSet =>

  def stressTest(c: StressConfig, ec : ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))) : StressResults = {
    // wait for all the imports to finish
    val postImportsF = for (i <- 1 to c.imports) yield Future(postLimsYml(mockLimsYml(i, s"$i-0001")))(ec)
    val postImports: Seq[Boolean] = for (f <- postImportsF) yield Await.result(f, Duration(60, "seconds"))
    // wait for all the queries to finish
    val getExpF = for (i <- 1 to c.imports) yield (for (j <- 1 to c.queryReps) yield Future(getExperimentOrRunCode(i))(ec))
    val getRuncodeF = for (i <- 1 to c.imports) yield (for (j <- 1 to c.queryReps) yield Future(getExperimentOrRunCode(s"$i-0001"))(ec))
    val getExp: Seq[(Boolean, Seq[LimsSubreadSet])] = for (f <- getExpF.flatten) yield Await.result(f, Duration(60, "seconds"))
    val getRuncode: Seq[(Boolean, Seq[LimsSubreadSet])] = for (f <- getRuncodeF.flatten) yield Await.result(f, Duration(60, "seconds"))
    // return the results
    new StressResults(postImports, getExp, getRuncode)
  }

  /**
   * GET request to lookup existing data by Experiment or Run Code
   */
  def getExperimentOrRunCode(expOrRunCode: String) : (Boolean, Seq[LimsSubreadSet]) = {
    implicit val defaultTimeout = RouteTestTimeout(Duration(30, "seconds"))

    Get(s"/subreadset/$expOrRunCode") ~> sealRoute(resolveRoutes) ~> check {
      (response.status.isSuccess, response.entity.data.asString.parseJson.convertTo[Seq[LimsSubreadSet]])
    }
  }
  def getExperimentOrRunCode(v: Int) : (Boolean, Seq[LimsSubreadSet]) = getExperimentOrRunCode(v.toString)

  /**
   * POST request with lims.yml content to make a database entry
   *
   * @param content
   * @return
   */
  def postLimsYml(content: String): Boolean = {
    val httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, HttpData(content)).asInstanceOf[HttpEntity.NonEmpty]
    val formFile = FormFile("file", httpEntity)
    val mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))
    loadData(content.getBytes)
    Post("/import", mfd) ~> sealRoute(importLimsRoutes) ~> check {
      response.status.isSuccess
    }
  }

  /**
   * Creates a mock lims.yml file, allowing override of all values
   *
   * The history and semantics of all of these was unknown to @jfalkner. We'll have to fill them in
   * and enforce constraints in a later iteration.
   *
   * @param expcode
   * @param runcode
   * @param path
   * @param user
   * @param uuid
   * @param tracefile
   * @param desc
   * @param well
   * @param cellbarcode
   * @param seqkitbarcode
   * @param cellindex
   * @param colnum
   * @param samplename
   * @param instid
   * @return
   */
  def mockLimsYml(
      // taken from `cat /net/pbi/collections/322/3220001/r54003_20160212_165105/1_A01/lims.yml`
      expcode: Int = 3220001,
      runcode: String = "3220001-0006",
      path: String = "file:///pbi/collections/322/3220001/r54003_20160212_165105/1_A01",
      user: String = "MilhouseUser",
      uuid: String = "1695780a2e7a0bb7cb1e186a3ee01deb",
      tracefile: String = "m54003_160212_165114.trc.h5",
      desc: String = "TestSample",
      well: String = "A01",
      cellbarcode: String = "00000133635908926745416610",
      seqkitbarcode: String = "002222100620000123119",
      cellindex: Int = 0,
      colnum: Int = 0,
      samplename: String = "TestSample",
      instid: Int = 90): String = {
    s"""expcode: $expcode
        |runcode: '$runcode'
        |path: '$path'
        |user: '$user'
        |uid: '$uuid'
        |tracefile: '$tracefile'
        |description: '$desc'
        |wellname: '$well'
        |cellbarcode: '$cellbarcode'
        |seqkitbarcode: '$seqkitbarcode'
        |cellindex: $cellindex
        |colnum: $colnum
        |samplename: '$samplename'
        |instid: $instid""".stripMargin
  }

  /**
   * Produces a mock version of *.subreadset.xml files
   *
   * This is based originally on /pbi/collections/312/3120145/r54009_20160426_164705/1_A01/m54009_160426_165001.subreadset.xml
   * @return
   */
  def mockSubreadset(
      uuid: UUID = UUID.fromString("5fe01e82-c694-4575-9173-c23c458dd0e1")
  ): String = {
    // yay, XML!
    s"""<?xml version="1.0" encoding="UTF-8"?>
      |<pbds:SubreadSet UniqueId="$uuid" MetaType="PacBio.DataSet.SubreadSet" TimeStampedName="SubreadSetCollection_160426_18460621" CreatedAt="2016-04-26T18:46:06Z" Name="54009_MBControlRun2_250pM_LambdaNEB" Tags="subreadset" Version="3.0.1" xmlns="http://pacificbiosciences.com/PacBioBaseDataModel.xsd" xmlns:pbdm="http://pacificbiosciences.com/PacBioDataModel.xsd" xmlns:pbds="http://pacificbiosciences.com/PacBioDatasets.xsd" xmlns:pbmeta="http://pacificbiosciences.com/PacBioCollectionMetadata.xsd" xmlns:pbsample="http://pacificbiosciences.com/PacBioSampleInfo.xsd" xmlns:pbrk="http://pacificbiosciences.com/PacBioReagentKit.xsd" xmlns:pbpn="http://pacificbiosciences.com/PacBioPartNumbers.xsd" xmlns:pbbase="http://pacificbiosciences.com/PacBioBaseDataModel.xsd">
      |<pbbase:ExternalResources>
      |<pbbase:ExternalResource Description="Points to the subreads bam file." MetaType="PacBio.SubreadFile.SubreadBamFile" Name="subreads bam" ResourceId="m54009_160426_165001.subreads.bam" TimeStampedName="pacbio_subreadfile_subreadbamfile-160426_184606114" UniqueId="9dd817f5-b4c6-47d5-a867-25fca1f74ab7" Version="3.0.1">
      |	<pbbase:FileIndices>
      |		<pbbase:FileIndex MetaType="PacBio.Index.PacBioIndex" ResourceId="m54009_160426_165001.subreads.bam.pbi" TimeStampedName="pacbio_index_pacbioindex-160426_184606114" UniqueId="760abaf2-3c38-4c97-94cd-78f3b0123ccb" Version="3.0.1" />
      |	</pbbase:FileIndices>
      |	<pbbase:ExternalResources>
      |		<pbbase:ExternalResource Description="Points to the scraps bam file." MetaType="PacBio.SubreadFile.ScrapsBamFile" Name="scraps bam" ResourceId="m54009_160426_165001.scraps.bam" TimeStampedName="pacbio_subreadfile_scrapsbamfile-160426_184606116" UniqueId="e8eaa04f-a296-4d75-add6-9325f109a817" Version="3.0.1">
      |			<pbbase:FileIndices>
      |				<pbbase:FileIndex MetaType="PacBio.Index.PacBioIndex" ResourceId="m54009_160426_165001.scraps.bam.pbi" TimeStampedName="pacbio_index_pacbioindex-160426_184606116" UniqueId="bcf5723f-a769-4594-8e52-1fde0a74acb9" Version="3.0.1" />
      |			</pbbase:FileIndices>
      |		</pbbase:ExternalResource>
      |		<pbbase:ExternalResource Description="Points to the control subreads bam file." MetaType="PacBio.SubreadFile.Control.SubreadBamFile" Name="subreads bam" ResourceId="m54009_160426_165001.control.subreads.bam" TimeStampedName="pacbio_subreadfile_control_subreadbamfile-160426_184606119" UniqueId="1ba7b6e2-94b3-46e7-b9a5-2cfae8e25105" Version="3.0.1">
      |			<pbbase:FileIndices>
      |				<pbbase:FileIndex MetaType="PacBio.Index.PacBioIndex" ResourceId="m54009_160426_165001.control.subreads.bam.pbi" TimeStampedName="pacbio_index_pacbioindex-160426_184606119" UniqueId="a29c1980-bcd6-4771-8870-265bbd13ced3" Version="3.0.1" />
      |			</pbbase:FileIndices>
      |			<pbbase:ExternalResources>
      |				<pbbase:ExternalResource Description="Points to the control scraps bam file." MetaType="PacBio.SubreadFile.Control.ScrapsBamFile" Name="scraps bam" ResourceId="m54009_160426_165001.control.scraps.bam" TimeStampedName="pacbio_subreadfile_control_scrapsbamfile-160426_184606121" UniqueId="4eb7ce85-3c4c-407b-b8e2-6dc8e55a24a2" Version="3.0.1">
      |					<pbbase:FileIndices>
      |						<pbbase:FileIndex MetaType="PacBio.Index.PacBioIndex" ResourceId="m54009_160426_165001.control.scraps.bam.pbi" TimeStampedName="pacbio_index_pacbioindex-160426_184606121" UniqueId="27beb1b2-8a58-42f4-959c-a648d0630833" Version="3.0.1" />
      |					</pbbase:FileIndices>
      |				</pbbase:ExternalResource>
      |			</pbbase:ExternalResources>
      |		</pbbase:ExternalResource>
      |		<pbbase:ExternalResource Description="Points to the summary sts.xml file." MetaType="PacBio.SubreadFile.ChipStatsFile" Name="Chipstats XML" ResourceId="m54009_160426_165001.sts.xml" TimeStampedName="pacbio_subreadfile_chipstatsfile-160426_184606121" UniqueId="aa9d1e94-3921-4bbb-8d12-b2e4e81252fa" Version="3.0.1" />
      |	</pbbase:ExternalResources>
      |</pbbase:ExternalResource>
      |</pbbase:ExternalResources>
      |<pbds:DataSetMetadata>
      |<pbds:TotalLength>610791860</pbds:TotalLength>
      |<pbds:NumRecords>511010</pbds:NumRecords>
      |<pbmeta:Collections>
      |<pbmeta:CollectionMetadata Context="m54009_160426_165001" Status="Ready" InstrumentId="54009" InstrumentName="Inst54009" UniqueId="dcb46fa5-3641-4529-a6fe-55be608d81b6" MetaType="CollectionMetadata" TimeStampedName="54009-CollectionMetadata-2016-47-26T16:47:05.480Z" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00">
      |	<pbmeta:InstCtrlVer>3.0.5.175014</pbmeta:InstCtrlVer>
      |	<pbmeta:SigProcVer>3.0.17</pbmeta:SigProcVer>
      |	<pbmeta:RunDetails>
      |		<pbmeta:TimeStampedName>r54009_20160426_164705</pbmeta:TimeStampedName>
      |		<pbmeta:Name>NoRS_Standard_Edna_Unrolled.1</pbmeta:Name>
      |		<pbmeta:CreatedBy>String</pbmeta:CreatedBy>
      |		<pbmeta:WhenCreated>0001-01-01T00:00:00</pbmeta:WhenCreated>
      |		<pbmeta:WhenStarted>2016-04-26T16:50:00.169503Z</pbmeta:WhenStarted>
      |	</pbmeta:RunDetails>
      |	<pbmeta:WellSample Name="lambdaNEB_A01" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00">
      |		<pbmeta:WellName>A01</pbmeta:WellName>
      |		<pbmeta:Concentration>0.0</pbmeta:Concentration>
      |		<pbmeta:InsertSize>2000</pbmeta:InsertSize>
      |		<pbmeta:SampleReuseEnabled>false</pbmeta:SampleReuseEnabled>
      |		<pbmeta:StageHotstartEnabled>false</pbmeta:StageHotstartEnabled>
      |		<pbmeta:SizeSelectionEnabled>true</pbmeta:SizeSelectionEnabled>
      |		<pbmeta:UseCount>0</pbmeta:UseCount>
      |	</pbmeta:WellSample>
      |	<pbmeta:Automation Name="Workflow_Magbead.py">
      |		<pbbase:AutomationParameters>
      |			<pbbase:AutomationParameter ValueDataType="Double" SimpleValue="0.0125" Name="Exposure" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00" />
      |			<pbbase:AutomationParameter ValueDataType="Double" SimpleValue="30" Name="MovieLength" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00" />
      |			<pbbase:AutomationParameter ValueDataType="JSON" SimpleValue="[[0,0,1080,1920]]" Name="sequencingPixelROI" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00" />
      |			<pbbase:AutomationParameter ValueDataType="JSON" SimpleValue="[[103,224,64,64],[103,704,64,64],[103,1184,64,64],[103,1664,64,64],[373,224,64,64],[373,704,64,64],[373,1184,64,64],[373,1664,64,64],[643,224,64,64],[643,704,64,64],[643,1184,64,64],[643,1664,64,64],[913,224,64,64],[913,704,64,64],[913,1184,64,64],[913,1664,64,64]]" Name="traceFilePixelROI" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00" />
      |			<pbbase:AutomationParameter ValueDataType="String" SimpleValue="false" Name="UseStageHotStart" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00" />
      |			<pbbase:AutomationParameter ValueDataType="Int32" SimpleValue="0" Name="CollectionNumber" />
      |			<pbbase:AutomationParameter ValueDataType="Int32" SimpleValue="0" Name="CellReuseIndex" />
      |			<pbbase:AutomationParameter ValueDataType="Int32" SimpleValue="2000" Name="InsertSize" />
      |		</pbbase:AutomationParameters>
      |	</pbmeta:Automation>
      |	<pbmeta:CollectionNumber>0</pbmeta:CollectionNumber>
      |	<pbmeta:CellIndex>0</pbmeta:CellIndex>
      |	<pbmeta:CellPac PartNumber="100-512-700" LotNumber="320245" Barcode="00000000635972862017106850" ExpirationDate="2016-09-08" />
      |	<pbmeta:TemplatePrepKit MinInsertSize="500" MaxInsertSize="20000" PartNumber="100-259-100" Barcode="222222100259100100716" Name="SMRTbell™ Template Prep Kit" Description="The SMRTbell™ Template Prep Kit contains reagent supplies to perform SMRTbell library preparations of primer-annealed SMRTbell libraries for insert sizes ranging from 500 bp to over 20 kb." Tags="Template Prep Kit, TPK" Version="1.0">
      |		<pbbase:LeftAdaptorSequence>ATCTCTCTCAACAACAACAACGGAGGAGGAGGAAAAGAGAGAGAT</pbbase:LeftAdaptorSequence>
      |		<pbbase:LeftPrimerSequence>aacggaggaggagga</pbbase:LeftPrimerSequence>
      |		<pbbase:RightAdaptorSequence>ATCTCTCTCAACAACAACAACGGAGGAGGAGGAAAAGAGAGAGAT</pbbase:RightAdaptorSequence>
      |		<pbbase:RightPrimerSequence>aacggaggaggagga</pbbase:RightPrimerSequence>
      |	</pbmeta:TemplatePrepKit>
      |	<pbmeta:BindingKit PartNumber="100-619-300" Barcode="222222100619300100716" Name="Sequel™ Binding Kit 1.0" Description="The Sequel Binding Kit 1.0 contains reagent supplies to bind prepared DNA template libraries to the Sequel Polymerase 1.0 in preparation for sequencing on the Sequel System. The result is a DNA polymerase/template complex. Sequel Binding Kit 1.0 should be used only with Sequel Sequencing Kit 1.0. Reagent quantities support 24 binding reactions." Tags="Binding Kit, BDK" Version="1.0" />
      |	<pbmeta:SequencingKitPlate PartNumber="100-620-000" LotNumber="007067" Barcode="007067100620000092416" ExpirationDate="2016-09-24">
      |		<pbrk:ReagentTubes PartNumber="100-619-700" LotNumber="706520" ExpirationDate="2018-05-12" />
      |		<pbrk:ReagentTubes PartNumber="100-619-600" LotNumber="007045" ExpirationDate="2020-02-28" />
      |	</pbmeta:SequencingKitPlate>
      |	<pbmeta:Primary>
      |		<pbmeta:AutomationName>SequelAlpha</pbmeta:AutomationName>
      |		<pbmeta:ConfigFileName>SqlPoC_SubCrf_2C2A-t2.xml</pbmeta:ConfigFileName>
      |		<pbmeta:SequencingCondition>DefaultPrimarySequencingCondition</pbmeta:SequencingCondition>
      |		<pbmeta:OutputOptions>
      |			<pbmeta:ResultsFolder>Analysis_Results</pbmeta:ResultsFolder>
      |			<pbmeta:CollectionPathUri>/pbi/collections/312/3120145/r54009_20160426_164705/1_A01/</pbmeta:CollectionPathUri>
      |			<pbmeta:CopyFiles>
      |				<pbmeta:CollectionFileCopy>Trace</pbmeta:CollectionFileCopy>
      |				<pbmeta:CollectionFileCopy>Fasta</pbmeta:CollectionFileCopy>
      |				<pbmeta:CollectionFileCopy>Bam</pbmeta:CollectionFileCopy>
      |				<pbmeta:CollectionFileCopy>Baz</pbmeta:CollectionFileCopy>
      |				<pbmeta:CollectionFileCopy>DarkFrame</pbmeta:CollectionFileCopy>
      |			</pbmeta:CopyFiles>
      |			<pbmeta:Readout>Pulses</pbmeta:Readout>
      |			<pbmeta:MetricsVerbosity>High</pbmeta:MetricsVerbosity>
      |		</pbmeta:OutputOptions>
      |	</pbmeta:Primary>
      |	<pbmeta:Secondary>
      |		<pbmeta:AutomationName>DefaultSecondaryAutomationName</pbmeta:AutomationName>
      |		<pbmeta:AutomationParameters>
      |			<pbmeta:AutomationParameter ValueDataType="String" SimpleValue="DefaultSecondaryAnalysisReferenceName" Name="Reference" CreatedAt="0001-01-01T00:00:00" ModifiedAt="0001-01-01T00:00:00" />
      |		</pbmeta:AutomationParameters>
      |		<pbmeta:CellCountInJob>0</pbmeta:CellCountInJob>
      |	</pbmeta:Secondary>
      |	<pbmeta:UserDefinedFields>
      |		<pbbase:DataEntities ValueDataType="String" SimpleValue="DefaultUserDefinedFieldLIMS" Name=" LIMS_IMPORT " />
      |	</pbmeta:UserDefinedFields>
      |</pbmeta:CollectionMetadata>
      |</pbmeta:Collections>
      |</pbds:DataSetMetadata>
      |</pbds:SubreadSet>
    """.stripMargin
  }
}

case class StressConfig (imports: Int, queryReps: Int)

class StressResults(
    val postImports: Seq[Boolean],
    val getExp: Seq[(Boolean, Seq[LimsSubreadSet])],
    val getRuncode: Seq[(Boolean, Seq[LimsSubreadSet])]) {

  def noImportFailures(): Boolean = !postImports.exists(_ == false)

  def noLookupFailures(): Boolean =
    !List(getExp, getRuncode).flatten.map(v => v._1).exists(_ == false)
}