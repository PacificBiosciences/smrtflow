package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Files, Paths}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.services.PacBioServiceErrors._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scalaz._
import Scalaz._

trait ValidateImportDataSetUtils {

  implicit val timeout = Timeout(4.seconds)

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

  def validateOpts(opts: ImportDataSetOptions): ValidateOptError = {
    (isPathExists(opts) |@| validateDataSetMetaType(opts))((_, _) => opts)
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
      case Right(x) => None
      case Left(er) => Some(s"Error $er")
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
  def resolveDataSet(datasetType: String, id: Int, dbActor: ActorRef): Future[ServiceDataSetMetadata] = {
    import JobsDaoActor._

    try {
      DataSetMetaTypes.toDataSetType(datasetType) match {
        case Some(DataSetMetaTypes.HdfSubread) => (dbActor ? GetHdfSubreadDataSetById(id)).mapTo[HdfSubreadServiceDataSet]
        case Some(DataSetMetaTypes.Subread) => (dbActor ? GetSubreadDataSetById(id)).mapTo[SubreadServiceDataSet]
        case Some(DataSetMetaTypes.Reference) => (dbActor ? GetReferenceDataSetById(id)).mapTo[ReferenceServiceDataSet]
        case Some(DataSetMetaTypes.Alignment) => (dbActor ? GetAlignmentDataSetById(id)).mapTo[AlignmentServiceDataSet]
        case Some(DataSetMetaTypes.Barcode) => (dbActor ? GetBarcodeDataSetsById(id)).mapTo[BarcodeServiceDataSet]
        case _ => Future.failed(new UnprocessableEntityError(s"Unsupported dataset type: $datasetType"))
        }
    } catch {
      case e: ResourceNotFoundError => Future.failed(new UnprocessableEntityError(s"Could not resolve dataset with id: $id"))
    }
  }

  def resolveDataSetByAny(datasetType: String, id: Either[Int,UUID], dbActor: ActorRef): Future[ServiceDataSetMetadata] = {
    import JobsDaoActor._
    id match {
      case Left(id_) => resolveDataSet(datasetType, id_, dbActor)
      case Right(uuid) => try {
        DataSetMetaTypes.toDataSetType(datasetType) match {
          case Some(DataSetMetaTypes.HdfSubread) => (dbActor ? GetHdfSubreadDataSetByUUID(uuid)).mapTo[HdfSubreadServiceDataSet]
          case Some(DataSetMetaTypes.Subread) => (dbActor ? GetSubreadDataSetByUUID(uuid)).mapTo[SubreadServiceDataSet]
          case Some(DataSetMetaTypes.Reference) => (dbActor ? GetReferenceDataSetByUUID(uuid)).mapTo[ReferenceServiceDataSet]
          case Some(DataSetMetaTypes.Alignment) => (dbActor ? GetAlignmentDataSetByUUID(uuid)).mapTo[AlignmentServiceDataSet]
          case Some(DataSetMetaTypes.Barcode) => (dbActor ? GetBarcodeDataSetsByUUID(uuid)).mapTo[BarcodeServiceDataSet]
          case _ => Future.failed(new UnprocessableEntityError(s"Unsupported dataset type: $datasetType"))
          }
      } catch {
        case e: ResourceNotFoundError => Future.failed(new UnprocessableEntityError(s"Could not resolve dataset with uuid: $uuid"))
      }
    }
  }
}

object ValidateImportDataSetUtils extends ValidateImportDataSetUtils
