
package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigException}

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondary.analysis.externaltools.{PacBioTestData,PbReports,CallSaWriterIndex}
import com.pacbio.secondary.smrtlink.client.ClientUtils
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

/**
 * Example config:
 *
 * {{{
 *   smrt-link-host = "smrtlink-bihourly"
 *   smrt-link-port = 8081
 * }}}
 */

object DataSetImportScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for DataSetImportScenario")
    require(PacBioTestData.isAvailable, "PacBioTestData must be configured for DataSetImportScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new DataSetImportScenario(
      c.getString("smrt-link-host"),
      getInt("smrt-link-port"))
  }
}

class DataSetImportScenario(host: String, port: Int)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with SmrtAnalysisSteps
    with ClientUtils {

  override val name = "DataSetImportScenario"

  override val smrtLinkClient = new AnalysisServiceAccessLayer(new URL("http", host, port, ""))

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
  val referenceSets: Var[Seq[ReferenceServiceDataSet]] = Var()
  val barcodeSets: Var[Seq[BarcodeServiceDataSet]] = Var()
  val hdfSubreadSets: Var[Seq[HdfSubreadServiceDataSet]] = Var()
  val alignmentSets: Var[Seq[AlignmentServiceDataSet]] = Var()
  val ccsSets: Var[Seq[ConsensusReadServiceDataSet]] = Var()
  val ccsAlignmentSets: Var[Seq[ConsensusAlignmentServiceDataSet]] = Var()
  val contigSets: Var[Seq[ContigServiceDataSet]] = Var()
  val dsFiles: Var[Seq[DataStoreServiceFile]] = Var()
  val job: Var[EngineJob] = Var()
  val jobId: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()
  val nBytes: Var[Int] = Var()
  val dsMeta: Var[DataSetMetaDataSet] = Var()
  val dsReports: Var[Seq[DataStoreReportFile]] = Var()
  val dsReport: Var[Report] = Var()
  val dataStore: Var[Seq[DataStoreServiceFile]] = Var()

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
    fail("UUID mismatch") IF subreadSet.mapWith(_.uuid) !=? dsMeta.mapWith(_.uuid)
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
    fail("Expected one ReferenceSet") IF referenceSets.mapWith(_.size) !=? 1
  ) ++ (if (! HAVE_SAWRITER) Seq() else Seq(
    jobId := ImportFasta(refFasta, Var("import-fasta")),
    jobStatus := WaitForJob(jobId),
    fail("Import FASTA job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    referenceSets := GetReferenceSets,
    fail("Expected two ReferenceSets") IF referenceSets.mapWith(_.size) !=? 2
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
    jobId := ImportFastaBarcodes(bcFasta, Var("import-barcodes")),
    jobStatus := WaitForJob(jobId),
    fail("Import barcodes job failed") IF jobStatus !=? EXIT_SUCCESS,
    barcodeSets := GetBarcodeSets,
    fail("Expected two BarcodeSets") IF barcodeSets.mapWith(_.size) !=? 2
  )
  val hdfSubreadTests = Seq(
    hdfSubreadSets := GetHdfSubreadSets,
    fail(MSG_DS_ERR) IF hdfSubreadSets ? (_.nonEmpty),
    jobId := ImportDataSet(hdfsubreads, ftHdfSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import HdfSubreads job failed") IF jobStatus !=? EXIT_SUCCESS,
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Expected one HdfSubreadSet") IF hdfSubreadSets.mapWith(_.size) !=? 1,
    jobId := ConvertRsMovie(rsMovie),
    jobStatus := WaitForJob(jobId),
    fail("Import RSII movie job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Expected two HdfSubreadSets") IF hdfSubreadSets.mapWith(_.size) !=? 2,
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
    // AlignmentSet
    alignmentSets := GetAlignmentSets,
    fail(MSG_DS_ERR) IF alignmentSets ? (_.nonEmpty),
    jobId := ImportDataSet(alignments, ftAlign),
    jobStatus := WaitForJob(jobId),
    fail("Import AlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    alignmentSets := GetAlignmentSets,
    fail("Expected one AlignmentSet") IF alignmentSets.mapWith(_.size) !=? 1,
    jobId := ImportDataSet(alignments2, ftAlign),
    jobStatus := WaitForJob(jobId),
    fail("Import AlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
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
    // ConsensusAlignmentSet
    ccsAlignmentSets := GetConsensusAlignmentSets,
    fail(MSG_DS_ERR) IF ccsAlignmentSets ? (_.nonEmpty),
    jobId := ImportDataSet(ccsAligned, ftCcsAlign),
    jobStatus := WaitForJob(jobId),
    fail("Import ConsensusAlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    ccsAlignmentSets := GetConsensusAlignmentSets,
    fail("Expected one ConsensusAlignmentSet") IF ccsAlignmentSets.mapWith(_.size) !=? 1
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
    fail("Expected RS Movie import to fail") IF jobStatus !=? EXIT_FAILURE
    // merge mixed dataset types
    // FIXME this won't even start a job, which is fine - should we still test?
    //jobId := MergeDataSets(Var(FileTypes.DS_SUBREADS.fileTypeId), Var(Seq(1,4)), Var("merge-subreads")),
    //jobStatus := WaitForJob(jobId),
    //fail("Expected merge job to fail") IF jobStatus !=? EXIT_FAILURE
  )
  override val steps = setupSteps ++ subreadTests ++ referenceTests ++ barcodeTests ++ hdfSubreadTests ++ otherTests ++ failureTests
}
