
package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigException}

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondary.analysis.externaltools.{PacBioTestData,PbReports}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

/**
 * Example config:
 *
 * {{{
 *   smrt-link-host = "smrtlink-bihourly"
 *   smrt-link-port = 8081
 *   run-xml-path = "/path/to/testdata/runDataModel.xml"
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
  extends Scenario with VarSteps with ConditionalSteps with IOSteps with SmrtLinkSteps with SmrtAnalysisSteps {

  override val name = "DataSetImportScenario"

  override val smrtLinkClient = new AnalysisServiceAccessLayer(new URL("http", host, port, ""))

  val MSG_DS_ERR = "DataSet database should be initially empty"
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val testdata = PacBioTestData()
  val usePbreports = PbReports.isAvailable()

  val subreadSets: Var[Seq[SubreadServiceDataSet]] = Var()
  val referenceSets: Var[Seq[ReferenceServiceDataSet]] = Var()
  val barcodeSets: Var[Seq[BarcodeServiceDataSet]] = Var()
  val hdfSubreadSets: Var[Seq[HdfSubreadServiceDataSet]] = Var()
  val alignmentSets: Var[Seq[AlignmentServiceDataSet]] = Var()
  val ccsSets: Var[Seq[ConsensusReadServiceDataSet]] = Var()
  val ccsAlignmentSets: Var[Seq[ConsensusAlignmentServiceDataSet]] = Var()
  val contigSets: Var[Seq[ContigServiceDataSet]] = Var()
  val jobId: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()

  override val steps = Seq(
    // SUBREADS
    subreadSets := GetSubreadSets,
    fail(MSG_DS_ERR) IF subreadSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("subreads-xml")), Var(FileTypes.DS_SUBREADS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(Var(testdata.getFile("subreads-sequel")), Var(FileTypes.DS_SUBREADS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    subreadSets := GetSubreadSets,
    fail("Expected two SubreadSets") IF subreadSets.mapWith(_.size) !=? 2,
    jobId := MergeDataSets(Var(FileTypes.DS_SUBREADS.fileTypeId), Var(Seq(1,2)), Var("merge-subreads")),
    jobStatus := WaitForJob(jobId),
    fail("Merge job failed") IF jobStatus !=? EXIT_SUCCESS,
    subreadSets := GetSubreadSets,
    fail("Expected three SubreadSets") IF subreadSets.mapWith(_.size) !=? 3,
    // REFERENCES
    referenceSets := GetReferenceSets,
    fail(MSG_DS_ERR) IF referenceSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("lambdaNEB")), Var(FileTypes.DS_REFERENCE.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    referenceSets := GetReferenceSets,
    fail("Expected one ReferenceSet") IF referenceSets.mapWith(_.size) !=? 1,
    // TODO would be nice to have a FASTA import here...
    // BARCODES
    barcodeSets := GetBarcodeSets,
    fail(MSG_DS_ERR) IF barcodeSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("barcodeset")), Var(FileTypes.DS_BARCODE.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    barcodeSets := GetBarcodeSets,
    fail("Expected one BarcodeSet") IF barcodeSets.mapWith(_.size) !=? 1,
    jobId := ImportFastaBarcodes(Var(testdata.getFile("barcode-fasta")), Var("import-barcodes")),
    jobStatus := WaitForJob(jobId),
    fail("Import barcodes job failed") IF jobStatus !=? EXIT_SUCCESS,
    barcodeSets := GetBarcodeSets,
    fail("Expected two BarcodeSet") IF barcodeSets.mapWith(_.size) !=? 2,
    // HdfSubreadSet
    hdfSubreadSets := GetHdfSubreadSets,
    fail(MSG_DS_ERR) IF hdfSubreadSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("hdfsubreads")), Var(FileTypes.DS_HDF_SUBREADS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import HdfSubreads job failed") IF jobStatus !=? EXIT_SUCCESS,
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Expected one HdfSubreadSet") IF hdfSubreadSets.mapWith(_.size) !=? 1,
    jobId := ConvertRsMovie(Var(testdata.getFile("rs-movie-metadata"))),
    jobStatus := WaitForJob(jobId),
    fail("Import RSII movie job failed") IF jobStatus !=? EXIT_SUCCESS,
    hdfSubreadSets := GetHdfSubreadSets,
    fail("Expected two HdfSubreadSets") IF hdfSubreadSets.mapWith(_.size) !=? 2,
    // ContigSet
    contigSets := GetContigSets,
    fail(MSG_DS_ERR) IF contigSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("contigset")), Var(FileTypes.DS_CONTIG.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import ContigSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    contigSets := GetContigSets,
    fail("Expected one ContigSet") IF contigSets.mapWith(_.size) !=? 1,
    // AlignmentSet
    alignmentSets := GetAlignmentSets,
    fail(MSG_DS_ERR) IF alignmentSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("aligned-xml")), Var(FileTypes.DS_ALIGNMENTS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import AlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    alignmentSets := GetAlignmentSets,
    fail("Expected one AlignmentSet") IF alignmentSets.mapWith(_.size) !=? 1,
    // ConsensusReadSet
    ccsSets := GetConsensusReadSets,
    fail(MSG_DS_ERR) IF ccsSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("rsii-ccs")), Var(FileTypes.DS_CCS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import ConsensusReadSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    ccsSets := GetConsensusReadSets,
    fail("Expected one ConsensusReadSet") IF ccsSets.mapWith(_.size) !=? 1,
    // ConsensusAlignmentSet
    ccsAlignmentSets := GetConsensusAlignmentSets,
    fail(MSG_DS_ERR) IF ccsAlignmentSets ? (_.nonEmpty),
    jobId := ImportDataSet(Var(testdata.getFile("rsii-ccs-aligned")), Var(FileTypes.DS_CCS_ALIGNMENTS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import ConsensusAlignmentSet job failed") IF jobStatus !=? EXIT_SUCCESS,
    ccsAlignmentSets := GetConsensusAlignmentSets,
    fail("Expected one ConsensusAlignmentSet") IF ccsAlignmentSets.mapWith(_.size) !=? 1,
    // FAILURE MODES
    // not a dataset
    jobId := ImportDataSet(Var(testdata.getFile("lambda-fasta")), Var(FileTypes.DS_REFERENCE.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Expected import to fail") IF jobStatus !=? EXIT_FAILURE,
    // wrong ds metatype
    jobId := ImportDataSet(Var(testdata.getFile("aligned-ds-2")), Var(FileTypes.DS_CONTIG.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Expected import to fail") IF jobStatus !=? EXIT_FAILURE,
    // not barcodes
    jobId := ImportFastaBarcodes(Var(testdata.getFile("misc-fasta")), Var("import-barcode-bad-fasta")),
    jobStatus := WaitForJob(jobId),
    fail("Expected barcode import to fail") IF jobStatus !=? EXIT_FAILURE,
    // wrong XML
    jobId := ConvertRsMovie(Var(testdata.getFile("hdfsubreads"))),
    jobStatus := WaitForJob(jobId),
    fail("Expected RS Movie import to fail") IF jobStatus !=? EXIT_FAILURE
  )
}
