package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Files, Paths}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.services.PacBioServiceErrors._
import com.pacbio.secondary.analysis.datasets.{DataSetFileUtils, DataSetMetaTypes}
import com.pacbio.secondary.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz._
import Scalaz._

// To avoid colliding with scalaz. This is pretty terrible naming
import scala.util.{Try => ScTry, Failure => ScFailure, Success => ScSuccess}

trait ValidateImportDataSetUtils extends DataSetFileUtils{

  implicit val timeout = Timeout(12.seconds)
  import CommonModelImplicits._


  type ValidationErrorMsg = String

  type ValidateOptError = ValidationNel[String, ImportDataSetOptions]

  def isPathExists(opts: ImportDataSetOptions): ValidateOptError = {
    if (Files.exists(Paths.get(opts.path))) opts.successNel
    else s"Failed to find DataSet ${opts.path}".failNel
  }

  def validateDataSetMetaType(opts: ImportDataSetOptions): ValidateOptError = {
    DataSetMetaTypes.toDataSetType(opts.datasetType.toString) match {
      case Some(_) => opts.successNel
      case _ => s"Invalid (or unsupported) DataSet ${opts.datasetType}".failNel
    }
  }

  /**
    * Validate the UUID and DataSet MetaType attributes provided in the XML
    *
    * @param opts
    * @return
    */
  def validateDataSetMetaData(opts: ImportDataSetOptions): ValidateOptError = {
    // This is naming is pretty terrible.
    ScTry (getDataSetMiniMeta(Paths.get(opts.path))) match {
      case ScSuccess(_) => opts.successNel
      case ScFailure(err) => s"Failed to parse UUID and MetaType from ${opts.path} (${err.getMessage})".failNel
    }
  }

  def validateOpts(opts: ImportDataSetOptions): ValidateOptError = {
    (isPathExists(opts) |@| validateDataSetMetaType(opts) |@| validateDataSetMetaData(opts))((_, _, _) => opts)
  }

  /**
   * Run all Import DataSet Options validation.
   *
   * 1. Validate path exists
   * 2. Validate
   *
   * @param opts Import dataset options
   * @return
   */
  def validateDataSetImportOpts(opts: ImportDataSetOptions): Option[ValidationErrorMsg] = {
    validateOpts(opts).toEither match {
      case Right(_) => None
      case Left(er) => Some(s"Errors: ${er.list.reduce(_ + "," + _)}")
    }
  }

  /**
   * Resolve DataSet By Dataset type and Int primary key
   *
   * @param datasetType DataSet Metdata type
   * @param id Int id of the dataset
   * @param dbActor Database Actor to resolve the DataSet resource
   * @return
   */
  def resolveDataSet(datasetType: String, id: IdAble, dbActor: ActorRef): Future[ServiceDataSetMetadata] = {
    import JobsDaoActor._

    try {
      DataSetMetaTypes.toDataSetType(datasetType) match {
        case Some(DataSetMetaTypes.HdfSubread) => (dbActor ? GetHdfSubreadDataSetById(id)).mapTo[HdfSubreadServiceDataSet]
        case Some(DataSetMetaTypes.Subread) => (dbActor ? GetSubreadDataSetById(id)).mapTo[SubreadServiceDataSet]
        case Some(DataSetMetaTypes.Reference) => (dbActor ? GetReferenceDataSetById(id)).mapTo[ReferenceServiceDataSet]
        case Some(DataSetMetaTypes.Alignment) => (dbActor ? GetAlignmentDataSetById(id)).mapTo[AlignmentServiceDataSet]
        case Some(DataSetMetaTypes.Barcode) => (dbActor ? GetBarcodeDataSetById(id)).mapTo[BarcodeServiceDataSet]
        case Some(DataSetMetaTypes.CCS) => (dbActor ? GetConsensusReadDataSetById(id)).mapTo[ConsensusReadServiceDataSet]
        case Some(DataSetMetaTypes.AlignmentCCS) => (dbActor ? GetConsensusAlignmentDataSetById(id)).mapTo[ConsensusAlignmentServiceDataSet]
        case Some(DataSetMetaTypes.Contig) => (dbActor ? GetContigDataSetById(id)).mapTo[ContigServiceDataSet]
        case Some(DataSetMetaTypes.GmapReference) => (dbActor ? GetGmapReferenceDataSetById(id)).mapTo[GmapReferenceServiceDataSet]
        case _ => Future.failed(new UnprocessableEntityError(s"Unsupported dataset type: $datasetType"))
        }
    } catch {
      case e: ResourceNotFoundError => Future.failed(new UnprocessableEntityError(s"Could not resolve dataset with id: $id"))
    }
  }
}

object ValidateImportDataSetUtils extends ValidateImportDataSetUtils
