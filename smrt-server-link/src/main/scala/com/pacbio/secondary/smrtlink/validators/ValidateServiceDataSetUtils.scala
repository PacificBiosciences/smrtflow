package com.pacbio.secondary.smrtlink.validators

import java.nio.file.{Files, Paths}

import akka.util.Timeout
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetFileUtils,
  DataSetMetaTypes
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.blocking
import scala.concurrent.ExecutionContext.Implicits.global

import scalaz.Scalaz._
import scalaz._

// To avoid colliding with scalaz. This is pretty terrible naming
import scala.util.{Failure => ScFailure, Success => ScSuccess, Try => ScTry}

/**
  * Attempting to centralize all general dataset validation here.
  * There's also DataSet type specific validaiton in analysis.
  *
  */
trait ValidateServiceDataSetUtils extends DataSetFileUtils {

  implicit val timeout = Timeout(12.seconds)
  import CommonModelImplicits._

  type ValidationErrorMsg = String

  // There's still one or two places this is used.
  def validateDataSetType(
      datasetType: String): Future[DataSetMetaTypes.DataSetMetaType] = {
    DataSetMetaTypes
      .toDataSetType(datasetType)
      .map(d => Future.successful(d))
      .getOrElse(Future.failed(new UnprocessableEntityError(
        s"Unsupported dataset type '$datasetType'")))
  }

  def validatePath(serviceDataSet: ServiceDataSetMetadata)
    : Future[ServiceDataSetMetadata] = {
    val p = Paths.get(serviceDataSet.path)
    if (Files.exists(p) && p.toFile.isFile) Future.successful(serviceDataSet)
    else
      Future.failed(new UnprocessableEntityError(
        s"Unable to find DataSet id:${serviceDataSet.id} UUID:${serviceDataSet.uuid} Path:$p"))
  }

  /**
    * Resolve the DataSet and Validate the Path to the XML file.
    *
    * In a future iteration, this should validate all the external resources in the DataSet XML file
    *
    * @param dsType DataSet MetaType
    * @param dsId DataSet Int
    * @return
    */
  def resolveAndValidatePath(dsType: DataSetMetaTypes.DataSetMetaType,
                             dsId: IdAble,
                             dao: JobsDao): Future[ServiceDataSetMetadata] = {

    import CommonModelImplicits._

    blocking {
      for {
        dsm <- ValidateServiceDataSetUtils.resolveDataSet(dsType, dsId, dao)
        validatedDataSet <- validatePath(dsm)
      } yield validatedDataSet
    }
  }

  def validateDataSetsExist(
      datasets: Seq[IdAble],
      dsType: DataSetMetaTypes.DataSetMetaType,
      dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] = {
    Future.sequence(
      datasets.map(id => resolveAndValidatePath(dsType, id, dao)))
  }

  /**
    * Resolve a list of DataSets from an IdAble and verify the XML file exists.
    *
    * @param dsType DataSet MetaType
    * @param ids    List of DataSet IdAbles
    * @param dao    db DAO
    * @return
    */
  def resolveInputs(dsType: DataSetMetaTypes.DataSetMetaType,
                    ids: Seq[IdAble],
                    dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] =
    Future.sequence(ids.map(x => resolveAndValidatePath(dsType, x, dao)))

  /**
    * Resolve DataSet By Dataset type and Int primary key
    *
    * @param datasetType DataSet Metdata type
    * @param id Int id of the dataset
    * @param dao Database interface
    * @return
    */
  def resolveDataSet(datasetType: DataSetMetaTypes.DataSetMetaType,
                     id: IdAble,
                     dao: JobsDao): Future[ServiceDataSetMetadata] = {

    val f1 = datasetType match {
      case DataSetMetaTypes.HdfSubread => dao.getHdfDataSetById(id)
      case DataSetMetaTypes.Subread => dao.getSubreadDataSetById(id)
      case DataSetMetaTypes.Reference => dao.getReferenceDataSetById(id)
      case DataSetMetaTypes.Alignment => dao.getAlignmentDataSetById(id)
      case DataSetMetaTypes.Barcode => dao.getBarcodeDataSetById(id)
      case DataSetMetaTypes.CCS => dao.getConsensusReadDataSetById(id)
      case DataSetMetaTypes.AlignmentCCS =>
        dao.getConsensusAlignmentDataSetById(id)
      case DataSetMetaTypes.Contig => dao.getContigDataSetById(id)
      case DataSetMetaTypes.GmapReference =>
        dao.getGmapReferenceDataSetById(id)
    }

    f1.recoverWith {
      case _: ResourceNotFoundError =>
        Future.failed(new UnprocessableEntityError(
          s"Could not resolve dataset $datasetType with id: ${id.toIdString}"))
    }
  }
}

object ValidateServiceDataSetUtils extends ValidateServiceDataSetUtils
