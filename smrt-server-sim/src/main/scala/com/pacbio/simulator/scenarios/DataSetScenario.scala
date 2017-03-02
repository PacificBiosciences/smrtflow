
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

import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigException}
import spray.httpx.UnsuccessfulResponseException
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.analysis.externaltools.{CallSaWriterIndex, PacBioTestData, PbReports}
import com.pacbio.secondary.smrtlink.client.{AnalysisServiceAccessLayer, ClientUtils}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.MockDataSetUtils
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._
import com.pacificbiosciences.pacbiodatasets._

/**
 * Example config:
 *
 * {{{
 *   smrt-link-host = "smrtlink-bihourly"
 *   smrt-link-port = 8081
 * }}}
 */

object DataSetScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for DataSetScenario")
    require(PacBioTestData.isAvailable, s"PacBioTestData must be configured for DataSetScenario. ${PacBioTestData.errorMessage}")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    // Should this be in smrtflow.test.* ?
    new DataSetScenario(
      c.getString("smrtflow.server.host"),
      getInt("smrtflow.server.port"))
  }
}

class DataSetScenario(host: String, port: Int)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with SmrtAnalysisSteps
    with ClientUtils {

  override val name = "DataSetScenario"

  override val smrtLinkClient = new AnalysisServiceAccessLayer(host, port)

  import CommonModelImplicits._

  val MSG_DS_ERR = "DataSet database should be initially empty"
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val testdata = PacBioTestData()
  val HAVE_PBREPORTS = PbReports.isAvailable()
  val HAVE_SAWRITER = CallSaWriterIndex.isAvailable()
  val N_SUBREAD_REPORTS = if (HAVE_PBREPORTS) 3 else 1
  val N_SUBREAD_MERGE_REPORTS = if (HAVE_PBREPORTS) 5 else 3

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
  val referenceSetDetails: Var[ReferenceSet] = Var()
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

  val ftSubreads = Var(FileTypes.DS_SUBREADS.fileTypeId)
  val ftHdfSubreads = Var(FileTypes.DS_HDF_SUBREADS.fileTypeId)
  val ftReference = Var(FileTypes.DS_REFERENCE.fileTypeId)
  val ftBarcodes = Var(FileTypes.DS_BARCODE.fileTypeId)
  val ftContigs = Var(FileTypes.DS_CONTIG.fileTypeId)
  val ftAlign = Var(FileTypes.DS_ALIGNMENTS.fileTypeId)
  val ftCcs = Var(FileTypes.DS_CCS.fileTypeId)
  val ftCcsAlign = Var(FileTypes.DS_CCS_ALIGNMENTS.fileTypeId)

  val subreads1 = Var(testdata.getFile("subreads-xml"))
  val subreadsUuid1 = Var(dsUuidFromPath(subreads1.get))
  val subreads2 = Var(testdata.getFile("subreads-sequel"))
  val subreadsUuid2 = Var(dsUuidFromPath(subreads2.get))
  val reference1 = Var(testdata.getFile("lambdaNEB"))
  val refFasta = Var(testdata.getFile("lambda-fasta"))
  val hdfSubreads = Var(testdata.getFile("hdfsubreads"))
  val barcodes = Var(testdata.getFile("barcodeset"))
  val bcFasta = Var(testdata.getFile("barcode-fasta"))
  val hdfsubreads = Var(testdata.getFile("hdfsubreads"))
  val rsMovie = Var(testdata.getFile("rs-movie-metadata"))
  val alignments = Var(testdata.getFile("aligned-xml"))
  val alignments2 = Var(testdata.getFile("aligned-ds-2"))
  val contigs = Var(testdata.getFile("contigset"))
  val ccs = Var(testdata.getFile("rsii-ccs"))
  val ccsAligned = Var(testdata.getFile("rsii-ccs-aligned"))

  val tmpDatasets = (1 to 4).map(_ => MockDataSetUtils.makeBarcodedSubreads)
  var tmpSubreads = tmpDatasets.map(x => Var(x._1))
  var tmpBarcodes = tmpDatasets.map(x => Var(x._2))
  val subreadsTmpUuid = Var(dsUuidFromPath(tmpDatasets(0)._1))

  private def getReportUuid(reports: Var[Seq[DataStoreReportFile]], reportId: String): Var[UUID] = {
    reports.mapWith(_.map(r => (r.reportTypeId, r.dataStoreFile.uuid)).toMap.get(reportId).get)
  }

  private def getReportTableValue(report: Report, tableId: String, columnId: String): Option[Any] = {
    for (table <- report.tables) {
      if (table.id == tableId) {
        for (column <- table.columns) {
          if (column.id == columnId) return Some(column.values(0))
        }
      }
    }
    return None
  }

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS
  )
  val subreadTests = Seq(
    subreadSets := GetSubreadSets,
    fail(MSG_DS_ERR) IF subreadSets ? (_.nonEmpty),
    jobId := ImportDataSet(subreads1, ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    dsMeta := GetDataSet(subreadsUuid1),
    subreadSetDetails := GetSubreadSetDetails(subreadsUuid1),
    fail(s"Wrong UUID") IF subreadSetDetails.mapWith(_.getUniqueId) !=? subreadsUuid1.get.toString,
    dsReports := GetSubreadSetReports(subreadsUuid1),
    fail(s"Expected one report") IF dsReports.mapWith(_.size) !=? 1,
    dataStore := GetImportJobDataStore(jobId),
    fail("Expected three datastore files") IF dataStore.mapWith(_.size) !=? 3,
    fail("Wrong UUID in datastore") IF dataStore.mapWith(_(2).uuid) !=? subreadsUuid1.get,
    jobId := ImportDataSet(subreads2, ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    dsMeta := GetDataSet(subreadsUuid2),
    subreadSets := GetSubreadSets,
    fail("Expected two SubreadSets") IF subreadSets.mapWith(_.size) !=? 2,
    // there will be 3 reports if pbreports is available
    dsReports := GetSubreadSetReports(subreadsUuid2),
    fail(s"Expected $N_SUBREAD_REPORTS reports") IF dsReports.mapWith(_.size) !=? N_SUBREAD_REPORTS,
    dsReport := GetReport(dsReports.mapWith(_(0).dataStoreFile.uuid)),
    fail("Wrong report UUID in datastore") IF dsReports.mapWith(_(0).dataStoreFile.uuid) !=? dsReport.mapWith(_.uuid),
    // merge SubreadSets
    jobId := MergeDataSets(ftSubreads, Var(Seq(1,2)), Var("merge-subreads")),
    jobStatus := WaitForJob(jobId),
    fail("Merge job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    subreadSets := GetSubreadSets,
    fail("Expected three SubreadSets") IF subreadSets.mapWith(_.size) !=? 3,
    dataStore := GetMergeJobDataStore(jobId),
    fail(s"Expected $N_SUBREAD_MERGE_REPORTS datastore files") IF dataStore.mapWith(_.size) !=? N_SUBREAD_MERGE_REPORTS,
    // XXX unfortunately there's no way to predict exactly where in the
    // datastore the SubreadSet will appear
    subreadSet := GetSubreadSet(subreadSets.mapWith(_.last.uuid)),
    dsMeta := GetDataSet(subreadSets.mapWith(_.last.uuid)),
    fail("UUID mismatch") IF subreadSet.mapWith(_.uuid) !=? dsMeta.mapWith(_.uuid),
    subreadSetDetails := GetSubreadSetDetails(subreadSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF subreadSetDetails.mapWith(_.getUniqueId) !=? subreadSets.mapWith(_.last.uuid.toString),
    fail("Expected two external resources for merged dataset") IF subreadSetDetails.mapWith(_.getExternalResources.getExternalResource.size) !=? 2,
    // count number of child jobs
    job := GetJobById(subreadSets.mapWith(_.head.jobId)),
    childJobs := GetJobChildren(job.mapWith(_.uuid)),
    fail("Expected 1 child job") IF childJobs.mapWith(_.size) !=? 1,
    DeleteJob(job.mapWith(_.uuid), Var(false)) SHOULD_RAISE classOf[UnsuccessfulResponseException],
    DeleteJob(job.mapWith(_.uuid), Var(true)) SHOULD_RAISE classOf[UnsuccessfulResponseException],
    childJobs := GetJobChildren(jobId),
    fail("Expected 0 children for merge job") IF childJobs.mapWith(_.size) !=? 0,
    // delete the merge job
    jobId2 := DeleteJob(jobId, Var(true)),
    fail("Expected original job to be returned") IF jobId2 !=? jobId,
    jobId := DeleteJob(jobId, Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    childJobs := GetJobChildren(job.mapWith(_.uuid)),
    fail("Expected 0 children after delete job") IF childJobs.mapWith(_.size) !=? 0,
    dsMeta := GetDataSet(subreadSets.mapWith(_.last.uuid)),
    fail("Expected isActive=false") IF dsMeta.mapWith(_.isActive) !=? false,
    job := GetJobById(subreadSets.mapWith(_.last.jobId)),
    dataStore := GetMergeJobDataStore(job.mapWith(_.uuid)),
    fail("Expected isActive=false") IF dataStore.mapWith(_.filter(f => f.isActive).size) !=? 0,
    subreadSets := GetSubreadSets,
    fail("Expected two SubreadSets") IF subreadSets.mapWith(_.size) !=? 2,
    // export SubreadSets
    jobId := ExportDataSets(ftSubreads, Var(Seq(1,2)), Var(Paths.get("subreadsets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS
  ) ++ (if (!HAVE_PBREPORTS) Seq() else Seq(
    // RUN QC FUNCTIONS (see run-qc-service.ts)
    dsReports := GetSubreadSetReports(subreadsUuid2),
    dsReport := GetReport(getReportUuid(dsReports, "pbreports.tasks.filter_stats_report_xml")),
    fail("Wrong report ID") IF dsReport.mapWith(_.id) !=? "raw_data_report",
    fail(s"Can't retrieve $RPT_NBASES") IF dsReport.mapWith(_.getAttributeLongValue(RPT_NBASES).get) !=? 1672335649,
    fail(s"Can't retrieve $RPT_READLENGTH") IF dsReport.mapWith(_.getAttributeLongValue(RPT_READLENGTH).get) !=? 4237,
    fail(s"Can't retrieve $RPT_INSERT") IF dsReport.mapWith(_.getAttributeLongValue(RPT_INSERT).get) !=? 4450,
    nBytes := GetDataStoreFileResource(dsReport.mapWith(_.uuid), dsReport.mapWith(_.getPlot(RPT_PLOT_GROUP, RPT_PLOT).get.image)),
    fail("Image has no content") IF nBytes ==? 0,
    dsReport := GetReport(getReportUuid(dsReports, "pbreports.tasks.loading_report_xml")),
    fail("Wrong report ID") IF dsReport.mapWith(_.id) !=? "loading_xml_report",
    fail(s"Can't retrieve $RPT_PRODZMWS") IF dsReport.mapWith(getReportTableValue(_, RPT_TABLE, RPT_PRODZMWS)) ==? None,
    fail(s"Can't retrieve productivity") IF dsReport.mapWith(getReportTableValue(_, RPT_TABLE, s"${RPT_PROD}_0")) ==? None
  ))
  val referenceTests = Seq(
    referenceSets := GetReferenceSets,
    fail(MSG_DS_ERR) IF referenceSets ? (_.nonEmpty),
    jobId := ImportDataSet(reference1, ftReference),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    referenceSets := GetReferenceSets,
    fail("Expected one ReferenceSet") IF referenceSets.mapWith(_.size) !=? 1,
    // export ReferenceSet
    jobId := ExportDataSets(ftReference, referenceSets.mapWith(_.map(d => d.id)), Var(Paths.get("referencesets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS
  ) ++ (if (! HAVE_SAWRITER) Seq() else Seq(
    // FASTA import tests (require sawriter)
    jobId := ImportFasta(refFasta, Var("import-fasta")),
    jobStatus := WaitForJob(jobId),
    fail("Import FASTA job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    referenceSets := GetReferenceSets,
    fail("Expected two ReferenceSets") IF referenceSets.mapWith(_.size) !=? 2,
    referenceSetDetails := GetReferenceSetDetails(referenceSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF referenceSetDetails.mapWith(_.getUniqueId) !=? referenceSets.mapWith(_.last.uuid.toString)
  ))
  val barcodeTests = Seq(
    barcodeSets := GetBarcodeSets,
    fail(MSG_DS_ERR) IF barcodeSets ? (_.nonEmpty),
    jobId := ImportDataSet(barcodes, ftBarcodes),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    barcodeSets := GetBarcodeSets,
    fail("Expected one BarcodeSet") IF barcodeSets.mapWith(_.size) !=? 1,
    barcodeSetDetails := GetBarcodeSetDetails(barcodeSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF barcodeSetDetails.mapWith(_.getUniqueId) !=? barcodeSets.mapWith(_.last.uuid.toString),
    // import FASTA
    jobId := ImportFastaBarcodes(bcFasta, Var("import-barcodes")),
    jobStatus := WaitForJob(jobId),
    fail("Import barcodes job failed") IF jobStatus !=? EXIT_SUCCESS,
    barcodeSets := GetBarcodeSets,
    fail("Expected two BarcodeSets") IF barcodeSets.mapWith(_.size) !=? 2,
    // export BarcodeSets
    jobId := ExportDataSets(ftBarcodes, barcodeSets.mapWith(_.map(d => d.id)), Var(Paths.get("barcodesets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS,
    // delete all jobs
    jobId := DeleteJob(jobId, Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete export job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJobById(barcodeSets.mapWith(_.head.jobId)),
    jobId := DeleteJob(job.mapWith(_.uuid), Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete BarcodeSet failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJobById(barcodeSets.mapWith(_.last.jobId)),
    jobId := DeleteJob(job.mapWith(_.uuid), Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete BarcodeSet failed") IF jobStatus !=? EXIT_SUCCESS,
    barcodeSets := GetBarcodeSets,
    fail("Expected zero BarcodeSets") IF barcodeSets.mapWith(_.size) !=? 0
  )
  val hdfSubreadTests = Seq(
    hdfSubreadSets := GetHdfSubreadSets,
    fail(MSG_DS_ERR) IF hdfSubreadSets ? (_.nonEmpty),
    jobId := ImportDataSet(hdfsubreads, ftHdfSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import HdfSubreads job failed") IF jobStatus !=? EXIT_SUCCESS,
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Expected one HdfSubreadSet") IF hdfSubreadSets.mapWith(_.size) !=? 1,
    hdfSubreadSetDetails := GetHdfSubreadSetDetails(hdfSubreadSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF hdfSubreadSetDetails.mapWith(_.getUniqueId) !=? hdfSubreadSets.mapWith(_.last.uuid.toString),
    // import RSII movie
    jobId := ConvertRsMovie(rsMovie),
    jobStatus := WaitForJob(jobId),
    fail("Import RSII movie job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Expected two HdfSubreadSets") IF hdfSubreadSets.mapWith(_.size) !=? 2,
    // export HdfSubreadSet
    jobId := ExportDataSets(ftHdfSubreads, hdfSubreadSets.mapWith(_.map(d => d.id)), Var(Paths.get("hdfsubreadsets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS,
    // merge HdfSubreadSets
    // XXX it's actually a little gross that this works, since these contain
    // the same bax.h5 files...
    jobId := MergeDataSets(ftHdfSubreads, hdfSubreadSets.mapWith(_.map(d => d.id)), Var("merge-hdfsubreads")),
    jobStatus := WaitForJob(jobId),
    fail("Merge job failed") IF jobStatus !=? EXIT_SUCCESS,
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Expected three HdfSubreadSets") IF hdfSubreadSets.mapWith(_.size) !=? 3
  )
  val otherTests = Seq(
    // ContigSet
    contigSets := GetContigSets,
    fail(MSG_DS_ERR) IF contigSets ? (_.nonEmpty),
    jobId := ImportDataSet(contigs, ftContigs),
    jobStatus := WaitForJob(jobId),
    fail("Import ContigSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    contigSets := GetContigSets,
    fail("Expected one ContigSet") IF contigSets.mapWith(_.size) !=? 1,
    jobId := ExportDataSets(ftContigs, contigSets.mapWith(_.map(d => d.id)), Var(Paths.get("contigsets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS,
    contigSetDetails := GetContigSetDetails(contigSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF contigSetDetails.mapWith(_.getUniqueId) !=? contigSets.mapWith(_.last.uuid.toString),
    // AlignmentSet
    alignmentSets := GetAlignmentSets,
    fail(MSG_DS_ERR) IF alignmentSets ? (_.nonEmpty),
    jobId := ImportDataSet(alignments, ftAlign),
    jobStatus := WaitForJob(jobId),
    fail("Import AlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    alignmentSets := GetAlignmentSets,
    fail("Expected one AlignmentSet") IF alignmentSets.mapWith(_.size) !=? 1,
    alignmentSetDetails := GetAlignmentSetDetails(alignmentSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF alignmentSetDetails.mapWith(_.getUniqueId) !=? alignmentSets.mapWith(_.last.uuid.toString),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(alignments2, ftAlign),
    jobStatus := WaitForJob(jobId),
    fail("Import AlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    // export
    jobId := ExportDataSets(ftAlign, alignmentSets.mapWith(_.map(d => d.id)), Var(Paths.get("alignmentsets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS,
    // merge
    alignmentSets := GetAlignmentSets,
    jobId := MergeDataSets(ftAlign, alignmentSets.mapWith(_.map(d => d.id)), Var("merge-alignments")),
    jobStatus := WaitForJob(jobId),
    fail("Merge job failed") IF jobStatus !=? EXIT_SUCCESS,
    alignmentSets := GetAlignmentSets,
    fail("Expected three AlignmentSets") IF alignmentSets.mapWith(_.size) !=? 3,
    // ConsensusReadSet
    ccsSets := GetConsensusReadSets,
    fail(MSG_DS_ERR) IF ccsSets ? (_.nonEmpty),
    jobId := ImportDataSet(ccs, ftCcs),
    jobStatus := WaitForJob(jobId),
    fail("Import ConsensusReadSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    ccsSets := GetConsensusReadSets,
    fail("Expected one ConsensusReadSet") IF ccsSets.mapWith(_.size) !=? 1,
    ccsSetDetails := GetConsensusReadSetDetails(ccsSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF ccsSetDetails.mapWith(_.getUniqueId) !=? ccsSets.mapWith(_.last.uuid.toString),
    jobId := ExportDataSets(ftCcs, ccsSets.mapWith(_.map(d => d.id)), Var(Paths.get("ccssets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS,
    // ConsensusAlignmentSet
    ccsAlignmentSets := GetConsensusAlignmentSets,
    fail(MSG_DS_ERR) IF ccsAlignmentSets ? (_.nonEmpty),
    jobId := ImportDataSet(ccsAligned, ftCcsAlign),
    jobStatus := WaitForJob(jobId),
    fail("Import ConsensusAlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    ccsAlignmentSets := GetConsensusAlignmentSets,
    fail("Expected one ConsensusAlignmentSet") IF ccsAlignmentSets.mapWith(_.size) !=? 1,
    ccsAlignmentSetDetails := GetConsensusAlignmentSetDetails(ccsAlignmentSets.mapWith(_.last.uuid)),
    fail("Wrong UUID") IF ccsAlignmentSetDetails.mapWith(_.getUniqueId) !=? ccsAlignmentSets.mapWith(_.last.uuid.toString),
    jobId := ExportDataSets(ftCcsAlign, ccsAlignmentSets.mapWith(_.map(d => d.id)), Var(Paths.get("ccsalignmentsets.zip").toAbsolutePath)),
    jobStatus := WaitForJob(jobId),
    fail("Export job failed") IF jobStatus !=? EXIT_SUCCESS,
    resp := DeleteDataSet(ccsAlignmentSets.mapWith(_.last.uuid)),
    ccsAlignmentSets := GetConsensusAlignmentSets,
    fail(MSG_DS_ERR) IF ccsAlignmentSets ? (_.nonEmpty)
  )
  // FAILURE MODES
  val failureTests = Seq(
    // not a dataset
    jobId := ImportDataSet(refFasta, ftReference),
    jobStatus := WaitForJob(jobId),
    fail("Expected import to fail") IF jobStatus !=? EXIT_FAILURE,
    // wrong ds metatype
    jobId := ImportDataSet(alignments2, ftContigs),
    jobStatus := WaitForJob(jobId),
    fail("Expected import to fail") IF jobStatus !=? EXIT_FAILURE,
    // not barcodes
    jobId := ImportFastaBarcodes(Var(testdata.getFile("misc-fasta")), Var("import-barcode-bad-fasta")),
    jobStatus := WaitForJob(jobId),
    fail("Expected barcode import to fail") IF jobStatus !=? EXIT_FAILURE,
    // wrong XML
    jobId := ConvertRsMovie(hdfSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Expected RS Movie import to fail") IF jobStatus !=? EXIT_FAILURE,
    // merge mixed dataset types
    MergeDataSets(ftSubreads, Var(Seq(1,4)), Var("merge-subreads")) SHOULD_RAISE classOf[UnsuccessfulResponseException]
  )
  val deleteTests = Seq(
    jobId := ImportDataSet(tmpSubreads(0), ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import SubreadSet failed") IF jobStatus !=? EXIT_SUCCESS,
    subreadSets := GetSubreadSets,
    fail("Expected three SubreadSets") IF subreadSets.mapWith(_.size) !=? 3,
    jobId := DeleteDataSets(ftSubreads, subreadSets.mapWith(ss => Seq(ss.last.id)), Var(true)),
    jobStatus := WaitForJob(jobId),
    fail("Delete SubreadSet failed") IF jobStatus !=? EXIT_SUCCESS,
    fail("Expected SubreadSet file to be deleted") IF tmpSubreads(0).mapWith(_.toFile.exists) !=? false,
    fail("Expected directory contents to be deleted") IF subreadSets.mapWith(ss => Paths.get(ss.last.path).getParent.toFile.listFiles.nonEmpty) !=? false,
    fail("Expected BarcodeSet to be untouched") IF tmpBarcodes(0).mapWith(_.toFile.exists) !=? true,
    // TODO check report?
    // failure modes
    referenceSets := GetReferenceSets,
    DeleteDataSets(ftReference, referenceSets.mapWith(rs => Seq(rs.last.id)), Var(true)) SHOULD_RAISE classOf[UnsuccessfulResponseException],
    // already deleted
    jobId := DeleteDataSets(ftSubreads, subreadSets.mapWith(ss => Seq(ss.last.id)), Var(true)),
    jobStatus := WaitForJob(jobId),
    fail("Expected job to fail") IF jobStatus !=? EXIT_FAILURE,
    subreadSets := GetSubreadSets,
    fail("Expected 2 SubreadSets") IF subreadSets.mapWith(_.size) !=? 2,
    // delete merged datasets
    jobId := ImportDataSet(tmpSubreads(1), ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import SubreadSet failed") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(tmpSubreads(2), ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import SubreadSet failed") IF jobStatus !=? EXIT_SUCCESS,
    subreadSets := GetSubreadSets,
    fail("Expected 3 SubreadSets") IF subreadSets.mapWith(_.size) !=? 4,
    jobId := MergeDataSets(ftSubreads, subreadSets.mapWith(_.takeRight(2).map(ds => ds.id)), Var("merge-subreads")),
    jobStatus := WaitForJob(jobId),
    fail("Merge SubreadSet failed") IF jobStatus !=? EXIT_SUCCESS,
    subreadSets := GetSubreadSets,
    fail("Expected 5 SubreadSets") IF subreadSets.mapWith(_.size) !=? 5,
    DeleteDataSets(ftSubreads, subreadSets.mapWith(ss => Seq(ss.last.id)), Var(true)),
    jobStatus := WaitForJob(jobId),
    fail("Delete SubreadSet failed") IF jobStatus !=? EXIT_SUCCESS,
    subreadSets := GetSubreadSets,
    fail("Expected 2 SubreadSets") IF subreadSets.mapWith(_.size) !=? 2
  )
  override val steps = setupSteps ++ subreadTests ++ referenceTests ++ barcodeTests ++ hdfSubreadTests ++ otherTests ++ failureTests ++ deleteTests
}
