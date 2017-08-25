package com.pacbio.secondary.smrtlink.validators

import java.nio.file.{Files, Paths}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.{DataSetFileUtils, DataSetMetaTypes}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors._

import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz.Scalaz._
import scalaz._

// To avoid colliding with scalaz. This is pretty terrible naming
import scala.util.{Failure => ScFailure, Success => ScSuccess, Try => ScTry}

trait ValidateImportDataSetUtils extends DataSetFileUtils{

  implicit val timeout = Timeout(12.seconds)


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
   * @param dao Database interface
   * @return
   */
  def resolveDataSet(datasetType: String, id: IdAble, dao: JobsDao): Future[ServiceDataSetMetadata] = {

    try {
      DataSetMetaTypes.toDataSetType(datasetType) match {
        case Some(DataSetMetaTypes.HdfSubread) => dao.getHdfDataSetById(id)
        case Some(DataSetMetaTypes.Subread) => dao.getSubreadDataSetById(id)
        case Some(DataSetMetaTypes.Reference) => dao.getReferenceDataSetById(id)
        case Some(DataSetMetaTypes.Alignment) => dao.getAlignmentDataSetById(id)
        case Some(DataSetMetaTypes.Barcode) => dao.getBarcodeDataSetById(id)
        case Some(DataSetMetaTypes.CCS) => dao.getConsensusReadDataSetById(id)
        case Some(DataSetMetaTypes.AlignmentCCS) => dao.getConsensusAlignmentDataSetById(id)
        case Some(DataSetMetaTypes.Contig) => dao.getContigDataSetById(id)
        case Some(DataSetMetaTypes.GmapReference) => dao.getGmapReferenceDataSetById(id)
        case _ => Future.failed(new UnprocessableEntityError(s"Unsupported dataset type: $datasetType"))
        }
    } catch {
      case e: ResourceNotFoundError => Future.failed(new UnprocessableEntityError(s"Could not resolve dataset with id: $id"))
    }
  }
}

object ValidateImportDataSetUtils extends ValidateImportDataSetUtils
