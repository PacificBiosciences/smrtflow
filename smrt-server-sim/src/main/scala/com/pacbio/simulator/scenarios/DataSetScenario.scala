// This tests the following service job types:
//   import-dataset
//   merge-datasets
//   export-datasets
//   convert-fasta-barcodes
//   convert-fasta-reference (only if 'sawriter' is available)
//   convert-rs-movie
//
// TODO: test GmapReferenceSet?

package com.pacbio.simulator.scenarios

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.util.Try

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils

import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.common.models.CommonModels
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  CallSaWriterIndex,
  PacBioTestData,
  PbReports
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.datasets.MockDataSetUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.client.{
  SmrtLinkServiceClient,
  ClientUtils
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object DataSetScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(config.isDefined,
            "Path to config file must be specified for DataSetScenario")
    require(
      PacBioTestData.isAvailable,
      s"PacBioTestData must be configured for DataSetScenario. ${PacBioTestData.errorMessage}")
    val c: Config = config.get

    val gmapAvailable = Try { c.getBoolean("gmapAvailable") }.toOption
      .getOrElse(false)
    new DataSetScenario(getHost(c), getPort(c), gmapAvailable)
  }
}

class DataSetScenario(host: String, port: Int, gmapAvailable: Boolean)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  override val name = "DataSetScenario"
  override val requirements = Seq("SL-1303")
  override val smrtLinkClient = new SmrtLinkServiceClient(host, port)

  import CommonModels._
  import CommonModelImplicits._

  val MSG_DS_ERR = "DataSet database should be initially empty"
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val testdata = PacBioTestData()
  val HAVE_PBREPORTS = PbReports.isAvailable()
  val HAVE_SAWRITER = CallSaWriterIndex.isAvailable()
  val N_SUBREAD_REPORTS = if (HAVE_PBREPORTS) 3 else 1
  val N_SUBREAD_MERGE_REPORTS = if (HAVE_PBREPORTS) 5 else 3
  val FAKE_REF =
    ">lambda_NEB3011\nGGGCGGCGACCTCGCGGGTTTTCGCTATTTATGAAAATTTTCCGGTTTAAGGCGTTTCCG\nTTCTTCTTCGTCATAACTTAATGTTTTTATTTAAAATACCCTCTGAAAAGAAAGGAAACG"

  // various Report model identifiers
  val RPT_NBASES = "raw_data_report.nbases"
  val RPT_READLENGTH = "raw_data_report.read_length"
  val RPT_INSERT = "raw_data_report.insert_length"
  val RPT_PLOT_GROUP = "raw_data_report.insert_length_plot_group"
  val RPT_PLOT = RPT_PLOT_GROUP + ".insert_length_plot_0"
  val RPT_TABLE = "loading_xml_report.loading_xml_table"
  val RPT_PRODZMWS = "loading_xml_report.loading_xml_table.productive_zmws"
  val RPT_PROD = "loading_xml_report.loading_xml_table.productivity"

  val subreadSets: Var[Seq[SubreadServiceDataSet]] = Var()
  val subreadSet: Var[SubreadServiceDataSet] = Var()
  val subreadSetDetails: Var[SubreadSet] = Var()
  val referenceSets: Var[Seq[ReferenceServiceDataSet]] = Var()
  val referenceSet: Var[ReferenceServiceDataSet] = Var()
  val referenceSetDetails: Var[ReferenceSet] = Var()
  val gmapReferenceSets: Var[Seq[GmapReferenceServiceDataSet]] = Var()
  val gmapReferenceSet: Var[GmapReferenceServiceDataSet] = Var()
  val gmapReferenceSetDetails: Var[GmapReferenceSet] = Var()
  val barcodeSets: Var[Seq[BarcodeServiceDataSet]] = Var()
  val barcodeSetDetails: Var[BarcodeSet] = Var()
  val hdfSubreadSets: Var[Seq[HdfSubreadServiceDataSet]] = Var()
  val hdfSubreadSetDetails: Var[HdfSubreadSet] = Var()
  val alignmentSets: Var[Seq[AlignmentServiceDataSet]] = Var()
  val alignmentSetDetails: Var[AlignmentSet] = Var()
  val ccsSets: Var[Seq[ConsensusReadServiceDataSet]] = Var()
  val ccsSetDetails: Var[ConsensusReadSet] = Var()
  val ccsAlignmentSets: Var[Seq[ConsensusAlignmentServiceDataSet]] = Var()
  val ccsAlignmentSetDetails: Var[ConsensusAlignmentSet] = Var()
  val contigSets: Var[Seq[ContigServiceDataSet]] = Var()
  val contigSetDetails: Var[ContigSet] = Var()
  val dsFiles: Var[Seq[DataStoreServiceFile]] = Var()
  val job: Var[EngineJob] = Var()
  val jobId: Var[UUID] = Var()
  val jobId2: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()
  val childJobs: Var[Seq[EngineJob]] = Var()
  val nBytes: Var[Int] = Var()
  val dsMeta: Var[DataSetMetaDataSet] = Var()
  val dsReports: Var[Seq[DataStoreReportFile]] = Var()
  val dsReport: Var[Report] = Var()
  val dataStore: Var[Seq[DataStoreServiceFile]] = Var()
  val resp: Var[String] = Var()

  val ftSubreads: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)
  val ftHdfSubreads: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.HdfSubread)
  val ftReference: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Reference)
  val ftBarcodes: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Barcode)
  val ftContigs: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Contig)
  val ftAlign: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Alignment)
  val ftCcs: Var[DataSetMetaTypes.DataSetMetaType] = Var(DataSetMetaTypes.CCS)
  val ftCcsAlign: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.AlignmentCCS)

  val subreads1 = Var(
    testdata.getTempDataSet("subreads-xml", tmpDirBase = "dataset contents"))
  val subreadsUuid1 = Var(getDataSetMiniMeta(subreads1.get).uuid)
  val subreads2 = Var(testdata.getTempDataSet("subreads-sequel"))
  val subreads3 = Var(testdata.getTempDataSet("subreads-sequel"))
  val subreadsUuid2 = Var(getDataSetMiniMeta(subreads2.get).uuid)
  val reference1 = Var(testdata.getTempDataSet("lambdaNEB"))
  val hdfSubreads = Var(testdata.getTempDataSet("hdfsubreads"))
  val barcodes = Var(testdata.getTempDataSet("barcodeset"))
  val bcFasta = Var(testdata.getFile("barcode-fasta"))
  val hdfsubreads = Var(testdata.getTempDataSet("hdfsubreads"))
  val rsMovie = Var(testdata.getFile("rs-movie-metadata"))
  val alignments = Var(testdata.getTempDataSet("aligned-xml"))
  val alignments2 = Var(testdata.getTempDataSet("aligned-ds-2"))
  val contigs = Var(testdata.getTempDataSet("contigset"))
  val ccs = Var(testdata.getTempDataSet("rsii-ccs"))
  val ccsAligned = Var(testdata.getTempDataSet("rsii-ccs-aligned"))

  val tmpDatasets = (1 to 4).map(_ => MockDataSetUtils.makeBarcodedSubreads)
  var tmpSubreads = tmpDatasets.map(x => Var(x._1))
  var tmpBarcodes = tmpDatasets.map(x => Var(x._2))
  val subreadsTmpUuid = Var(getDataSetMiniMeta(tmpDatasets(0)._1).uuid)
  // this deliberately preserves the original UUID
  val tmpSubreads2 = Var(
    MockDataSetUtils
      .makeTmpDataset(subreads1.get, DataSetMetaTypes.Subread, false))
  val refFasta = Var(Files.createTempFile("lambda", ".fasta"))
  FileUtils.writeStringToFile(refFasta.get.toFile, FAKE_REF, "UTF-8")

  private def getReportUuid(reports: Var[Seq[DataStoreReportFile]],
                            reportId: String): Var[UUID] = {
    reports.mapWith(
      _.map(r => (r.reportTypeId, r.dataStoreFile.uuid)).toMap
        .get(reportId)
        .get)
  }

  private def getZipFileName(prefix: String) =
    Files
      .createTempDirectory("export")
      .resolve(s"${prefix}.zip")
      .toAbsolutePath
  private val subreadsZip = Var(getZipFileName("subreads"))

  private def wasNotIncremented[T](v1: Var[Seq[T]],
                                   v2: Var[Seq[T]],
                                   n: Int = 1) =
    v1.mapWith(_.size + n) !=? v2.mapWith(_.size)

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS
  )
  val subreadTests = Seq(
    subreadSets := GetSubreadSets,
    jobId := ImportDataSet(subreads1, ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    dsMeta := GetDataSet(subreadsUuid1),
    fail(s"Wrong path") IF dsMeta.mapWith(_.path) !=? subreads1.get.toString,
    subreadSetDetails := GetSubreadSetDetails(subreadsUuid1),
    fail(s"Wrong UUID") IF subreadSetDetails
      .mapWith(_.getUniqueId) !=? subreadsUuid1.get.toString,
    dsReports := GetSubreadSetReports(subreadsUuid1),
    fail(s"Expected no reports") IF dsReports.mapWith(_.size) !=? 0,
    dataStore := GetJobDataStore(jobId),
    fail("Expected three datastore files") IF dataStore.mapWith(_.size) !=? 2,
    fail("Wrong UUID in datastore") IF dataStore.mapWith { dss =>
      dss.filter(_.fileTypeId == FileTypes.DS_SUBREADS.fileTypeId).head.uuid
    } !=? subreadsUuid1.get,
    jobId := ImportDataSet(subreads2, ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    dsMeta := GetDataSet(subreadsUuid2),
    // there will be 3 reports if pbreports is available
    dsReports := GetSubreadSetReports(subreadsUuid2),
    fail(s"Expected $N_SUBREAD_REPORTS reports") IF dsReports
      .mapWith(_.size) !=? N_SUBREAD_REPORTS,
    dsReport := GetJobReport(dsMeta.mapWith(_.jobId),
                             dsReports.mapWith(_(0).dataStoreFile.uuid)),
    fail("Wrong report UUID in datastore") IF dsReports.mapWith(
      _(0).dataStoreFile.uuid) !=? dsReport.mapWith(_.uuid),
    // merge SubreadSets
    subreadSets := GetSubreadSets,
    jobId := MergeDataSets(
      ftSubreads,
      subreadSets.mapWith(_.takeRight(2).map(ss => ss.id)),
      Var("merge-subreads")),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    subreadSets := GetSubreadSets,
    dataStore := GetJobDataStore(jobId),
    fail(s"Expected $N_SUBREAD_MERGE_REPORTS datastore files") IF dataStore
      .mapWith(_.size) !=? N_SUBREAD_MERGE_REPORTS,
    subreadSet := GetSubreadSet(subreadSets.mapWith(_.last.uuid)),
    dsMeta := GetDataSet(subreadSets.mapWith(_.last.uuid)),
    fail("UUID mismatch") IF subreadSet.mapWith(_.uuid) !=? dsMeta.mapWith(
      _.uuid),
    subreadSetDetails := GetSubreadSetDetails(
      subreadSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF subreadSetDetails
      .mapWith(_.getUniqueId) !=? subreadSets.mapWith(_.last.uuid.toString),
    fail("Expected two external resources for merged dataset") IF subreadSetDetails
      .mapWith(_.getExternalResources.getExternalResource.size) !=? 2,
    // count number of child jobs
    job := GetJobById(subreadSets.mapWith(_.takeRight(3).head.jobId)),
    childJobs := GetJobChildren(job.mapWith(_.uuid)),
    fail("Expected 1 child job") IF childJobs.mapWith(_.size) !=? 1,
    DeleteJob(job.mapWith(_.uuid), Var(false)) SHOULD_RAISE classOf[Exception],
    DeleteJob(job.mapWith(_.uuid), Var(true)) SHOULD_RAISE classOf[Exception],
    childJobs := GetJobChildren(jobId),
    fail("Expected 0 children for merge job") IF childJobs
      .mapWith(_.size) !=? 0,
    // delete the merge job
    jobId2 := DeleteJob(jobId, Var(true)),
    // fail("Expected original job to be returned") IF jobId2 !=? jobId,
    jobId := DeleteJob(jobId, Var(false)),
    job := WaitForSuccessfulJob(jobId),
    childJobs := GetJobChildren(job.mapWith(_.uuid)),
    fail("Expected 0 children after delete job") IF childJobs
      .mapWith(_.size) !=? 0,
    dsMeta := GetDataSet(subreadSets.mapWith(_.last.uuid)),
    fail(s"Expected isActive=false for $dsMeta") IF dsMeta
      .mapWith(_.isActive) !=? false,
    job := GetJobById(subreadSets.mapWith(_.last.jobId)),
    dataStore := GetJobDataStore(job.mapWith(_.uuid)),
    fail("Expected isActive=false") IF dataStore.mapWith(
      _.filter(f => f.isActive).size) !=? 0,
    // export SubreadSets
    subreadSets := GetSubreadSets,
    jobId := ExportDataSets(
      ftSubreads,
      subreadSets.mapWith(ss => ss.takeRight(2).map(_.id)),
      subreadsZip),
    job := WaitForSuccessfulJob(jobId),
    // attempt to export to already existing .zip file
    ExportDataSets(ftSubreads,
                   subreadSets.mapWith(ss => ss.takeRight(2).map(_.id)),
                   subreadsZip) SHOULD_RAISE classOf[Exception]
  ) ++ (if (!HAVE_PBREPORTS) Seq()
        else
          Seq(
            // RUN QC FUNCTIONS (see run-qc-service.ts)
            dsMeta := GetDataSet(subreadsUuid2),
            dsReports := GetSubreadSetReports(subreadsUuid2),
            dsReport := GetJobReport(
              dsMeta.mapWith(_.jobId),
              getReportUuid(dsReports,
                            "pbreports.tasks.filter_stats_report_xml")),
            fail("Wrong report ID") IF dsReport
              .mapWith(_.id) !=? "raw_data_report",
            fail(s"Can't retrieve $RPT_NBASES") IF dsReport.mapWith(
              _.getAttributeLongValue(RPT_NBASES).get) !=? 19825,
            fail(s"Can't retrieve $RPT_READLENGTH") IF dsReport.mapWith(
              _.getAttributeLongValue(RPT_READLENGTH).get) !=? 9913,
            fail(s"Can't retrieve $RPT_INSERT") IF dsReport.mapWith(
              _.getAttributeLongValue(RPT_INSERT).get) !=? 1168,
            nBytes := GetDataStoreFileResource(
              dsReport.mapWith(_.uuid),
              dsReport.mapWith(_.getPlot(RPT_PLOT_GROUP, RPT_PLOT).get.image)),
            fail("Image has no content") IF nBytes ==? 0,
            dsReport := GetJobReport(
              dsMeta.mapWith(_.jobId),
              getReportUuid(dsReports, "pbreports.tasks.loading_report_xml")),
            fail("Wrong report ID") IF dsReport
              .mapWith(_.id) !=? "loading_xml_report",
            fail(s"Can't retrieve $RPT_PRODZMWS") IF dsReport.mapWith(
              _.getFirstValueFromTableColumn(RPT_TABLE, RPT_PRODZMWS)) ==? None,
            fail(s"Can't retrieve productivity") IF dsReport
              .mapWith(_.getFirstValueFromTableColumn(
                RPT_TABLE,
                s"${RPT_PROD}_0_n")) ==? None
          ))
  val referenceTests = Seq(
    referenceSets := GetReferenceSets,
    jobId := ImportDataSet(reference1, ftReference),
    job := WaitForSuccessfulJob(jobId),
    referenceSets := GetReferenceSets,
    // export ReferenceSet
    jobId := ExportDataSets(
      ftReference,
      referenceSets.mapWith(_.takeRight(1).map(d => d.id)),
      Var(getZipFileName("references"))),
    job := WaitForSuccessfulJob(jobId)
  ) ++ (if (!HAVE_SAWRITER) Seq()
        else
          Seq(
            // FASTA import tests (require sawriter)
            jobId := ImportFasta(refFasta, Var("import_fasta")),
            job := WaitForSuccessfulJob(jobId),
            fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
              _.smrtlinkVersion) ==? None,
            referenceSets := GetReferenceSets,
            referenceSetDetails := GetReferenceSetDetails(
              referenceSets.mapWith(_.last.uuid)),
            fail("Wrong UUID") IF referenceSetDetails
              .mapWith(_.getUniqueId) !=? referenceSets.mapWith(
              _.last.uuid.toString),
            referenceSet := GetReferenceSet(
              referenceSets.mapWith(_.last.uuid)),
            fail("Wrong ploidy") IF referenceSet
              .mapWith(_.ploidy) !=? "haploid",
            fail("Wrong organism") IF referenceSet
              .mapWith(_.organism) !=? "lambda",
            fail("Wrong name") IF referenceSet
              .mapWith(_.name) !=? "import_fasta"
          ))
  // GmapReferenceSet import tests (require gmap_build)
  val gmapReferenceTests =
    if (!gmapAvailable) Seq()
    else {
      Seq(
        jobId := ImportFastaGmap(refFasta, Var("import_fasta_gmap")),
        job := WaitForSuccessfulJob(jobId),
        fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
          _.smrtlinkVersion) ==? None,
        gmapReferenceSets := GetGmapReferenceSets,
        gmapReferenceSetDetails := GetGmapReferenceSetDetails(
          gmapReferenceSets.mapWith(_.last.uuid)),
        fail("Wrong UUID") IF gmapReferenceSetDetails
          .mapWith(_.getUniqueId) !=? gmapReferenceSets.mapWith(
          _.last.uuid.toString),
        gmapReferenceSet := GetGmapReferenceSet(
          gmapReferenceSets.mapWith(_.last.uuid)),
        fail("Wrong ploidy") IF gmapReferenceSet
          .mapWith(_.ploidy) !=? "haploid",
        fail("Wrong organism") IF gmapReferenceSet
          .mapWith(_.organism) !=? "lambda",
        fail("Wrong name") IF gmapReferenceSet
          .mapWith(_.name) !=? "import_fasta_gmap"
      )
    }
  val barcodeTests = Seq(
    barcodeSets := GetBarcodeSets,
    jobId := ImportDataSet(barcodes, ftBarcodes),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    barcodeSets := GetBarcodeSets,
    barcodeSetDetails := GetBarcodeSetDetails(getUuid(barcodes)),
    fail("Wrong UUID") IF barcodeSetDetails
      .mapWith(_.getUniqueId) !=? barcodeSets.mapWith(_.last.uuid.toString),
    // import FASTA
    jobId := ImportFastaBarcodes(bcFasta, Var("import-barcodes")),
    job := WaitForSuccessfulJob(jobId),
    barcodeSets := GetBarcodeSets,
    barcodeSetDetails := GetBarcodeSetDetails(
      barcodeSets.mapWith(_.last.uuid)),
    // export BarcodeSets
    jobId := ExportDataSets(ftBarcodes,
                            barcodeSets.mapWith(_.takeRight(2).map(d => d.id)),
                            Var(getZipFileName("barcodes"))),
    job := WaitForSuccessfulJob(jobId),
    // delete all jobs
    jobId := DeleteJob(jobId, Var(false)),
    job := WaitForSuccessfulJob(jobId),
    job := GetJobById(barcodeSets.mapWith(_.takeRight(2).head.jobId)),
    jobId := DeleteJob(job.mapWith(_.uuid), Var(false)),
    job := WaitForSuccessfulJob(jobId),
    job := GetJobById(barcodeSets.mapWith(_.last.jobId)),
    jobId := DeleteJob(job.mapWith(_.uuid), Var(false)),
    job := WaitForSuccessfulJob(jobId)
  )
  val hdfSubreadTests = Seq(
    hdfSubreadSets := GetHdfSubreadSets,
    jobId := ImportDataSet(hdfsubreads, ftHdfSubreads),
    job := WaitForSuccessfulJob(jobId),
    hdfSubreadSetDetails := GetHdfSubreadSetDetails(getUuid(hdfsubreads)),
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Wrong UUID") IF hdfSubreadSetDetails
      .mapWith(_.getUniqueId) !=? hdfSubreadSets.mapWith(_.last.uuid.toString),
    // import RSII movie
    jobId := ConvertRsMovie(rsMovie),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    hdfSubreadSets := GetHdfSubreadSets,
    // export HdfSubreadSet
    jobId := ExportDataSets(
      ftHdfSubreads,
      hdfSubreadSets.mapWith(_.takeRight(2).map(d => d.id)),
      Var(getZipFileName("hdfsubreads"))),
    job := WaitForSuccessfulJob(jobId),
    // merge HdfSubreadSets
    // XXX it's actually a little gross that this works, since these contain
    // the same bax.h5 files...
    jobId := MergeDataSets(
      ftHdfSubreads,
      hdfSubreadSets.mapWith(_.takeRight(2).map(d => d.id)),
      Var("merge-hdfsubreads")),
    job := WaitForSuccessfulJob(jobId)
  )
  val otherTests = Seq(
    // ContigSet
    jobId := ImportDataSet(contigs, ftContigs),
    job := WaitForSuccessfulJob(jobId),
    contigSets := GetContigSets,
    jobId := ExportDataSets(ftContigs,
                            contigSets.mapWith(_.takeRight(1).map(d => d.id)),
                            Var(getZipFileName("contigs"))),
    job := WaitForSuccessfulJob(jobId),
    contigSetDetails := GetContigSetDetails(getUuid(contigs)),
    fail("UUID mismatch between tables") IF contigSetDetails.mapWith(
      _.getUniqueId) !=? contigSets.mapWith(_.last.uuid.toString),
    // AlignmentSet
    jobId := ImportDataSet(alignments, ftAlign),
    job := WaitForSuccessfulJob(jobId),
    alignmentSets := GetAlignmentSets,
    alignmentSetDetails := GetAlignmentSetDetails(getUuid(alignments)),
    fail("UUID mismatch") IF alignmentSetDetails
      .mapWith(_.getUniqueId) !=? alignmentSets.mapWith(_.last.uuid.toString),
    jobId := ImportDataSet(alignments2, ftAlign),
    job := WaitForSuccessfulJob(jobId),
    // export
    jobId := ExportDataSets(ftAlign,
                            alignmentSets.mapWith(_.map(d => d.id)),
                            Var(getZipFileName("alignments"))),
    job := WaitForSuccessfulJob(jobId),
    // ConsensusReadSet
    jobId := ImportDataSet(ccs, ftCcs),
    job := WaitForSuccessfulJob(jobId),
    ccsSets := GetConsensusReadSets,
    ccsSetDetails := GetConsensusReadSetDetails(getUuid(ccs)),
    fail("Wrong UUID") IF ccsSetDetails.mapWith(_.getUniqueId) !=? ccsSets
      .mapWith(_.last.uuid.toString),
    jobId := ExportDataSets(ftCcs,
                            ccsSets.mapWith(_.map(d => d.id)),
                            Var(getZipFileName("ccs"))),
    job := WaitForSuccessfulJob(jobId),
    // ConsensusAlignmentSet
    ccsAlignmentSets := GetConsensusAlignmentSets,
    jobId := ImportDataSet(ccsAligned, ftCcsAlign),
    job := WaitForSuccessfulJob(jobId),
    ccsAlignmentSets := GetConsensusAlignmentSets,
    ccsAlignmentSetDetails := GetConsensusAlignmentSetDetails(
      getUuid(ccsAligned)),
    fail("Wrong UUID") IF ccsAlignmentSetDetails
      .mapWith(_.getUniqueId) !=? ccsAlignmentSets.mapWith(
      _.last.uuid.toString),
    jobId := ExportDataSets(
      ftCcsAlign,
      ccsAlignmentSets.mapWith(_.takeRight(1).map(d => d.id)),
      Var(getZipFileName("ccsalignments"))),
    job := WaitForSuccessfulJob(jobId)
  )
  // FAILURE MODES
  val failureTests = Seq(
    // not a dataset
    ImportDataSet(refFasta, ftReference) SHOULD_RAISE classOf[Exception],
    // wrong ds metatype
    // FIXME to be removed since we can get the metatype from the XML instead
    // of making it a POST parameter
    jobId := ImportDataSet(subreads3, ftContigs),
    jobStatus := WaitForJob(jobId),
    fail("Expected import to fail") IF jobStatus !=? EXIT_FAILURE,
    // not barcodes
    jobId := ImportFastaBarcodes(Var(testdata.getFile("misc-fasta")),
                                 Var("import-barcode-bad-fasta")),
    jobStatus := WaitForJob(jobId),
    fail("Expected barcode import to fail") IF jobStatus !=? EXIT_FAILURE,
    // wrong XML
    jobId := ConvertRsMovie(hdfSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Expected RS Movie import to fail") IF jobStatus !=? EXIT_FAILURE
  )
  val deleteTests = Seq(
    jobId := ImportDataSet(tmpSubreads(0), ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    subreadSets := GetSubreadSets,
    jobId := DeleteDataSets(ftSubreads,
                            subreadSets.mapWith(ss => Seq(ss.last.id)),
                            Var(true)),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected SubreadSet file to be deleted") IF tmpSubreads(0).mapWith(
      _.toFile.exists) !=? false,
    fail("Expected directory contents to be deleted") IF subreadSets.mapWith(
      ss =>
        Paths.get(ss.last.path).getParent.toFile.listFiles.nonEmpty) !=? false,
    fail("Expected BarcodeSet to be untouched") IF tmpBarcodes(0).mapWith(
      _.toFile.exists) !=? true,
    // TODO check report?
    // failure modes
    referenceSets := GetReferenceSets,
    DeleteDataSets(ftReference,
                   referenceSets.mapWith(rs => Seq(rs.last.id)),
                   Var(true)) SHOULD_RAISE classOf[Exception],
    // already deleted
    jobId := DeleteDataSets(ftSubreads,
                            subreadSets.mapWith(ss => Seq(ss.last.id)),
                            Var(true)),
    jobStatus := WaitForJob(jobId),
    fail("Expected job to fail") IF jobStatus !=? EXIT_FAILURE,
    subreadSets := GetSubreadSets,
    // delete merged datasets
    jobId := ImportDataSet(tmpSubreads(1), ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    jobId := ImportDataSet(tmpSubreads(2), ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    subreadSets := GetSubreadSets,
    jobId := MergeDataSets(
      ftSubreads,
      subreadSets.mapWith(_.takeRight(2).map(ds => ds.id)),
      Var("merge-subreads")),
    job := WaitForSuccessfulJob(jobId),
    subreadSets := GetSubreadSets,
    DeleteDataSets(ftSubreads,
                   subreadSets.mapWith(ss => Seq(ss.last.id)),
                   Var(true)),
    job := WaitForSuccessfulJob(jobId),
    // combined export+delete
    jobId := ImportDataSet(tmpSubreads(3), ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    subreadSets := GetSubreadSets,
    dsMeta := GetDataSet(subreadSets.mapWith(_.last.uuid)),
    fail("Wrong SubreadSet UUID") IF dsMeta
      .mapWith(_.uuid) !=? getDataSetMiniMeta(tmpSubreads(3).get).uuid,
    fail("Expected last SubreadSet to be active") IF dsMeta
      .mapWith(_.isActive) !=? true,
    jobId := ExportDataSets(ftSubreads,
                            subreadSets.mapWith(ss => Seq(ss.last.id)),
                            Var(getZipFileName("subreads")),
                            Var(true)),
    job := WaitForSuccessfulJob(jobId),
    childJobs := GetDatasetDeleteJobs,
    job := WaitForSuccessfulJob(childJobs.mapWith(_.last.uuid)),
    dsMeta := GetDataSet(subreadSets.mapWith(_.last.uuid)),
    fail("Expected last SubreadSet to be inactive") IF dsMeta.mapWith(
      _.isActive) !=? false
  )
  val reimportTests = Seq(
    jobId := ImportDataSet(tmpSubreads2, ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    subreadSets := GetSubreadSets,
    fail("Multiple dataset have the same UUID") IF subreadSets.mapWith { ss =>
      ss.filter(_.uuid == subreadsUuid1.get).size
    } !=? 1,
    fail("Path did not change") IF subreadSets.mapWith { ss =>
      ss.filter(_.uuid == subreadsUuid1.get).last.path
    } !=? tmpSubreads2.get.toString
  )
  override val steps = setupSteps ++ subreadTests ++ referenceTests ++ gmapReferenceTests ++ barcodeTests ++ hdfSubreadTests ++ otherTests ++ failureTests ++ deleteTests ++ reimportTests
}
