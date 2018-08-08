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
  PacBioTestResources,
  PbReports
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.datasets.MockDataSetUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  ExportDataSets => RawExportDataSets
}
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

import scala.concurrent.duration._

object DataSetScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {

    val c = verifyRequiredConfig(config)
    val testResources = verifyConfiguredWithTestResources(c)
    val client = new SmrtLinkServiceClient(getHost(c), getPort(c))

    val gmapAvailable = Try { c.getBoolean("gmapAvailable") }.toOption
      .getOrElse(false)

    new DataSetScenario(client: SmrtLinkServiceClient,
                        testResources: PacBioTestResources,
                        gmapAvailable: Boolean)
  }
}

class DataSetScenario(client: SmrtLinkServiceClient,
                      testResources: PacBioTestResources,
                      gmapAvailable: Boolean)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  override val name = "DataSetScenario"
  override val requirements = Seq("SL-1303")
  override val smrtLinkClient = client

  import CommonModels._
  import CommonModelImplicits._

  val MSG_DS_ERR = "DataSet database should be initially empty"
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

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

  private def getTmp(ix: String, setNewUuid: Boolean = false): Path =
    testResources
      .findById(ix)
      .get
      .getTempDataSetFile(setNewUuid = setNewUuid)
      .path

  val subreads1 = Var(
    testResources
      .findById("subreads-xml")
      .get
      .getTempDataSetFile(tmpDirBase = "dataset contents")
      .path)

  // Many of these should be updated to create New UUID to enable
  // rerunning of the tests, or clarify the usecases and expected behavior
  val subreadsUuid1 = Var(getDataSetMiniMeta(subreads1.get).uuid)
  val subreads2 = Var(getTmp("subreads-sequel"))
  val subreads3 = Var(getTmp("subreads-sequel"))
  val subreadsUuid2 = Var(getDataSetMiniMeta(subreads2.get).uuid)
  val subreadUuids = Var(Seq(subreadsUuid1.get, subreadsUuid2.get))
  val reference1 = Var(getTmp("lambdaNEB"))

  val hdfSubreads1 = Var(getTmp("hdfsubreads", true))
  val hdfSubreads2 = Var(getTmp("hdfsubreads", true))

  val barcodes = Var(getTmp("barcodeset"))
  val barcodesUuid = barcodes.mapWith(getDataSetMiniMeta(_).uuid)
  val bcFasta = Var(testResources.findById("barcode-fasta").get.path)
  val rsMovie = Var(testResources.findById("rs-movie-metadata").get.path)
  val alignments = Var(getTmp("aligned-xml", true))
  val alignments2 = Var(getTmp("aligned-ds-2", true))
  val contigs = Var(getTmp("contigset"))
  val ccs = Var(getTmp("rsii-ccs"))
  val ccsAligned = Var(getTmp("rsii-ccs-aligned"))

  lazy val tmpDatasets: Seq[(Path, Path)] =
    (1 to 4).map(_ => MockDataSetUtils.makeBarcodedSubreads(testResources))
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

  lazy val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS
  )

  lazy val subreadTests = Seq(
    subreadSets := GetSubreadSets,
    jobId := ImportDataSet(subreads1, ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    dsMeta := GetDataSet(subreadsUuid1),
    fail(s"Wrong path") IF dsMeta.mapWith(_.path) !=? subreads1.get.toString,
    subreadSetDetails := GetSubreadSetDetails(subreadsUuid1),
    fail(s"Wrong SubreadSet UUID") IF subreadSetDetails
      .mapWith(_.getUniqueId) !=? subreadsUuid1.get.toString,
    dsReports := GetSubreadSetReports(subreadsUuid1),
    fail(s"Expected no reports") IF dsReports.mapWith(_.size) !=? 0,
    dataStore := GetJobDataStore(jobId),
    fail("Expected two datastore files") IF dataStore.mapWith(_.size) !=? 2,
    fail("Wrong SubreadSet UUID in datastore") IF dataStore.mapWith { dss =>
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
    jobId := MergeDataSets(ftSubreads, subreadUuids, Var("merge-subreads")),
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
    fail("Wrong SubreadSet UUID") IF subreadSetDetails
      .mapWith(_.getUniqueId) !=? subreadSets.mapWith(_.last.uuid.toString),
    fail("Expected two external resources for merged dataset") IF subreadSetDetails
      .mapWith(_.getExternalResources.getExternalResource.size) !=? 2,
    // count number of child jobs
    job := GetJobById(subreadSets.mapWith(_.takeRight(3).head.jobId)),
    childJobs := GetJobChildren(job.mapWith(_.uuid)),
    // FIXME. This is might have dependency on the history?
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
      _.count(f => f.isActive)) !=? 0,
    // export SubreadSets
    subreadSets := GetSubreadSets,
    jobId := ExportDataSets(ftSubreads, subreadUuids, subreadsZip),
    job := WaitForSuccessfulJob(jobId),
    // attempt to export to already existing .zip file
    ExportDataSets(ftSubreads, subreadUuids, subreadsZip) SHOULD_RAISE classOf[
      Exception]
  )

  lazy val referenceTests = Seq(
    referenceSets := GetReferenceSets,
    jobId := ImportDataSet(reference1, ftReference),
    job := WaitForSuccessfulJob(jobId),
    referenceSets := GetReferenceSets,
    // export ReferenceSet
    jobId := ExportDataSets(
      ftReference,
      referenceSets.mapWith(_.takeRight(1).map(d => d.uuid)),
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
            dataStore := GetJobDataStore(jobId),
            fail("Expected 1 ReferenceSet dataset type in DataStore") IF dataStore
              .mapWith(_.count(
                _.fileTypeId == DataSetMetaTypes.Reference.fileType.fileTypeId)) !=? 1,
            referenceSets := GetReferenceSets,
            referenceSetDetails := GetReferenceSetDetails(
              referenceSets.mapWith(_.sortBy(_.id).last.uuid)),
            fail("Wrong ReferenceSet UUID") IF referenceSetDetails
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
  lazy val gmapReferenceTests =
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
        fail("Wrong GmapReferenceSet UUID") IF gmapReferenceSetDetails
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
  lazy val barcodeTests = Seq(
    barcodeSets := GetBarcodeSets,
    jobId := ImportDataSet(barcodes, ftBarcodes),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    barcodeSets := GetBarcodeSets,
    barcodeSetDetails := GetBarcodeSetDetails(getUuid(barcodes)),
    fail("Wrong BarcodeSet UUID") IF barcodeSetDetails
      .mapWith(_.getUniqueId) !=? barcodesUuid.get.toString,
    // import FASTA
    jobId := ImportFastaBarcodes(bcFasta, Var("sim-import-barcodes")),
    job := WaitForSuccessfulJob(jobId),
    barcodeSets := GetBarcodeSets,
    barcodeSetDetails := GetBarcodeSetDetails(
      barcodeSets.mapWith(_.sortBy(_.id).last.uuid)),
    // export BarcodeSets
    jobId := ExportDataSets(
      ftBarcodes,
      barcodeSets.mapWith(_.takeRight(2).map(d => d.uuid)),
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

  lazy val hdfSubreadTests = Seq(
    hdfSubreadSets := GetHdfSubreadSets,
    jobId := ImportDataSet(hdfSubreads2, ftHdfSubreads),
    job := WaitForSuccessfulJob(jobId),
    dataStore := GetJobDataStore(jobId),
    fail("Expected 1 HdfSubreadSet dataset type in DataStore") IF dataStore
      .mapWith(_.count(
        _.fileTypeId == DataSetMetaTypes.HdfSubread.fileType.fileTypeId)) !=? 1,
    hdfSubreadSetDetails := GetHdfSubreadSetDetails(getUuid(hdfSubreads2)),
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Wrong HdfSubreadSet UUID") IF hdfSubreadSetDetails
      .mapWith(_.getUniqueId) !=? hdfSubreadSets.mapWith(
      _.sortBy(_.id).last.uuid.toString),
    // import RSII movie
    jobId := ConvertRsMovie(rsMovie),
    job := WaitForSuccessfulJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    hdfSubreadSets := GetHdfSubreadSets,
    // export HdfSubreadSet
    jobId := ExportDataSets(
      ftHdfSubreads,
      hdfSubreadSets.mapWith(_.takeRight(2).map(d => d.uuid)),
      Var(getZipFileName("hdfsubreads"))),
    job := WaitForSuccessfulJob(jobId),
    // merge HdfSubreadSets
    // XXX it's actually a little gross that this works, since these contain
    // the same bax.h5 files...
    jobId := MergeDataSets(
      ftHdfSubreads,
      hdfSubreadSets.mapWith(_.takeRight(2).map(d => d.uuid)),
      Var("merge-hdfsubreads")),
    job := WaitForSuccessfulJob(jobId)
  )
  lazy val otherTests = Seq(
    // ContigSet
    jobId := ImportDataSet(contigs, ftContigs),
    job := WaitForSuccessfulJob(jobId),
    contigSets := GetContigSets,
    jobId := ExportDataSets(
      ftContigs,
      contigSets.mapWith(_.takeRight(1).map(d => d.uuid)),
      Var(getZipFileName("contigs"))),
    job := WaitForSuccessfulJob(jobId),
    contigSetDetails := GetContigSetDetails(getUuid(contigs)),
    fail("Wrong ContigSet UUID") IF contigSetDetails
      .mapWith(_.getUniqueId) !=? contigSets.mapWith(
      _.sortBy(_.id).last.uuid.toString),
    // AlignmentSet
    jobId := ImportDataSet(alignments, ftAlign),
    job := WaitForSuccessfulJob(jobId),
    alignmentSets := GetAlignmentSets,
    alignmentSetDetails := GetAlignmentSetDetails(getUuid(alignments)),
    fail("Wrong AlignmentSet UUID") IF alignmentSetDetails
      .mapWith(_.getUniqueId) !=? alignmentSets.mapWith(
      _.sortBy(_.id).last.uuid.toString),
    jobId := ImportDataSet(alignments2, ftAlign),
    job := WaitForSuccessfulJob(jobId),
    // export
    jobId := ExportDataSets(ftAlign,
                            alignmentSets.mapWith(_.map(d => d.uuid)),
                            Var(getZipFileName("alignments"))),
    job := WaitForSuccessfulJob(jobId),
    // ConsensusReadSet
    jobId := ImportDataSet(ccs, ftCcs),
    job := WaitForSuccessfulJob(jobId),
    ccsSets := GetConsensusReadSets,
    ccsSetDetails := GetConsensusReadSetDetails(getUuid(ccs)),
    fail("Wrong CCSSet UUID") IF ccsSetDetails
      .mapWith(_.getUniqueId) !=? getUuid(ccs).get.toString,
    jobId := ExportDataSets(ftCcs,
                            ccsSets.mapWith(_.map(d => d.uuid)),
                            Var(getZipFileName("ccs"))),
    job := WaitForSuccessfulJob(jobId),
    // ConsensusAlignmentSet
    ccsAlignmentSets := GetConsensusAlignmentSets,
    jobId := ImportDataSet(ccsAligned, ftCcsAlign),
    job := WaitForSuccessfulJob(jobId),
    ccsAlignmentSets := GetConsensusAlignmentSets,
    ccsAlignmentSetDetails := GetConsensusAlignmentSetDetails(
      getUuid(ccsAligned)),
    fail("Wrong CCSAlignmentSet UUID") IF ccsAlignmentSetDetails
      .mapWith(_.getUniqueId) !=? getUuid(ccsAligned).get.toString,
    jobId := ExportDataSets(ftCcsAlign,
                            getUuid(ccsAligned).mapWith(Seq(_)),
                            Var(getZipFileName("ccsalignments"))),
    job := WaitForSuccessfulJob(jobId)
  )
  // FAILURE MODES
  lazy val failureTests = Seq(
    // not a dataset
    ImportDataSet(refFasta, ftReference) SHOULD_RAISE classOf[Exception],
    // Wrong ds metatype and will fail at Job creation/validation time at the service level
    ImportDataSet(subreads3, ftContigs) SHOULD_RAISE classOf[Exception],
    // wrong XML
    jobId := ConvertRsMovie(hdfSubreads1),
    jobStatus := WaitForJob(jobId),
    fail("Expected RS Movie import to fail") IF jobStatus !=? EXIT_FAILURE
  )

  lazy val deleteTests = Seq(
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
      subreadSets.mapWith(_.takeRight(2).map(ds => ds.uuid)),
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
                            subreadSets.mapWith(ss => Seq(ss.last.uuid)),
                            Var(getZipFileName("subreads")),
                            Var(true)),
    job := WaitForSuccessfulJob(jobId),
    childJobs := GetDatasetDeleteJobs,
    job := WaitForSuccessfulJob(childJobs.mapWith(_.last.uuid)),
    dsMeta := GetDataSet(subreadSets.mapWith(_.last.uuid)),
    fail("Expected last SubreadSet to be inactive") IF dsMeta.mapWith(
      _.isActive) !=? false
  )

  lazy val reimportTests = Seq(
    jobId := ImportDataSet(tmpSubreads2, ftSubreads),
    job := WaitForSuccessfulJob(jobId),
    subreadSets := GetSubreadSets,
    fail("Multiple dataset have the same UUID") IF subreadSets.mapWith { ss =>
      ss.count(_.uuid == subreadsUuid1.get)
    } !=? 1,
    fail("Path did not change") IF subreadSets.mapWith { ss =>
      ss.filter(_.uuid == subreadsUuid1.get).last.path
    } !=? tmpSubreads2.get.toString
  )

  val testImportDataSetIds = Seq("barcodeset", "subreads-sequel", "lambdaNEB")

  lazy val importDataSetsXmlZipSteps: Seq[Step] = testImportDataSetIds.map {
    f =>
      val testResource = testResources.findById(f).get
      andLog(s"Loaded TestResource $testResource")
      val copiedResource = testResource.getTempDataSetFile(setNewUuid = true)
      RunImportDataSetsXmlZip(copiedResource,
                              s"Import DataSet Zip pacbiotestdata id:$f",
                              3.minutes)
  }

  override val steps = setupSteps ++ subreadTests ++ referenceTests ++ gmapReferenceTests ++ barcodeTests ++
    hdfSubreadTests ++ otherTests ++ failureTests ++ deleteTests ++ reimportTests ++ importDataSetsXmlZipSteps
}
