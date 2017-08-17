package com.pacbio.secondary.smrtlink.validators

import java.nio.file.{Files, Path}

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{DataSetLoader, DataSetValidator}
import com.pacificbiosciences.pacbiodatasets.SubreadSet

import scala.util.{Failure, Success, Try}

/**
 *
 * Pre-flight job utils for dealing the DataSets
 *
 * Created by mkocher on 9/17/15.
 */
object DataSetValidators {

  case class InvalidDataSetError(msg: String) extends Exception(msg)


  /**
   * Validate that the DataSet has at least one External Resource
   *
   * @param ds Subread DataSet
   * @return
   */
  def validateAtLeastOneExternalResource(ds: SubreadSet): Either[InvalidDataSetError, SubreadSet] = {

    ds.getExternalResources.getExternalResource
    Right(ds)
  }

  /**
   * Validate that All root level External Resource ResourceId (URIs) are found
   *
   * @param ds Subread DataSet
   * @return
   */
  def validateExternalResourcePathsExist(ds: SubreadSet): Either[InvalidDataSetError, SubreadSet] = {
    Right(ds)
  }

  def validateSubreadSet(path: Path): Either[Seq[InvalidDataSetError], Path] = {
    if (Files.exists(path)) {

      val ds = DataSetLoader.loadSubreadSet(path)
      val valdiators = Seq(
        validateAtLeastOneExternalResource(ds),
        validateExternalResourcePathsExist(ds))

      val failures = valdiators.map(x => x.left.toOption).flatMap(x => x)
      if (failures.nonEmpty) {
        Left(failures)
      } else {
        Right(path)
      }
    } else {
      val ex = InvalidDataSetError(s"Unable to find dataset $path")
      Left(Seq(ex))
    }
  }

  /**
   * General Util for Validating a DataSet
   *
   * @param dst
   * @param path
   * @return
   */
  def validateDataSet(dst: DataSetMetaType, path: Path): Either[InvalidDataSetError, Path] = {

    if (Files.exists(path)) {
      val rootDir = path.getParent
      dst match {
        case DataSetMetaTypes.Reference =>
          Try {
            DataSetValidator.validate(DataSetLoader.loadReferenceSet(path), rootDir)
          } match {
            case Success(x) => Right(path)
            case Failure(ex) => Left(InvalidDataSetError(s"Failed to load ReferenceSet from $path ${ex.getMessage}"))
          }
        case DataSetMetaTypes.Subread =>
          Try {
            DataSetValidator.validate(DataSetLoader.loadSubreadSet(path), rootDir)
          } match {
            case Success(x) => Right(path)
            case Failure(ex) => Left(InvalidDataSetError(s"Failed to load ReferenceSet from $path ${ex.getMessage}"))
          }
        case DataSetMetaTypes.HdfSubread =>
          Try {
            DataSetValidator.validate(DataSetLoader.loadHdfSubreadSet(path), rootDir)
          } match {
            case Success(x) => Right(path)
            case Failure(ex) => Left(InvalidDataSetError(s"Failed to load ReferenceSet from $path ${ex.getMessage}"))
          }
        case DataSetMetaTypes.Alignment =>
          Try {
            DataSetValidator.validate(DataSetLoader.loadAlignmentSet(path), rootDir)
          } match {
            case Success(x) => Right(path)
            case Failure(ex) => Left(InvalidDataSetError(s"Failed to load ReferenceSet from $path ${ex.getMessage}"))
          }
        case x =>
          Left(InvalidDataSetError(s"Unsupported dataset type $x"))
      }
    } else {
      Left(InvalidDataSetError(s"Unable to find $path"))
    }
  }
}

object SubreadDataSetValidators {


}
