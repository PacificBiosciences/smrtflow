package com.pacbio.secondary.smrtlink.models

import java.nio.file.{Files, Path}

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.analysis.datasets.io.{DataSetLoader, DataSetValidator}
import com.pacificbiosciences.pacbiodatasets._

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
   * General Util for Validating a DataSet
   * @param dst
   * @param path
   * @return
   */
  def validateDataSet(dst: DataSetMetaType, path: Path): Either[InvalidDataSetError, Path] = {

    def vx[T <: DataSetType](f: Path => T): Path => Either[InvalidDataSetError, Path] = { px =>
      Try { DataSetValidator.validate(f(px), px.getParent) } match {
        case Success(_) => Right(px)
        case Failure(ex) => Left(InvalidDataSetError(s"Failed to load DataSet $px ${ex.getMessage}"))
      }
    }

    val validateReferenceSet = vx[ReferenceSet](DataSetLoader.loadReferenceSet)
    val validateSubreadSet = vx[SubreadSet](DataSetLoader.loadSubreadSet)
    val validateHdfSubreadSet = vx[HdfSubreadSet](DataSetLoader.loadHdfSubreadSet)
    val validateAlignmentSet = vx[AlignmentSet](DataSetLoader.loadAlignmentSet)
    val validateBarcodeSet = vx[BarcodeSet](DataSetLoader.loadBarcodeSet)
    val validateGmapReferenceSet = vx[GmapReferenceSet](DataSetLoader.loadGmapReferenceSet)
    val validateConsensusReadSet = vx[ConsensusReadSet](DataSetLoader.loadConsensusReadSet)
    val validateConsensusAlignmentSet = vx[ConsensusAlignmentSet](DataSetLoader.loadConsensusAlignmentSet)
    val validateContigSet = vx[ContigSet](DataSetLoader.loadContigSet)

    if (Files.exists(path)) {
      dst match {
        case DataSetMetaTypes.Reference => validateReferenceSet(path)
        case DataSetMetaTypes.Subread => validateSubreadSet(path)
        case DataSetMetaTypes.HdfSubread => validateHdfSubreadSet(path)
        case DataSetMetaTypes.Alignment => validateAlignmentSet(path)
        case DataSetMetaTypes.Barcode => validateBarcodeSet(path)
        case DataSetMetaTypes.GmapReference => validateGmapReferenceSet(path)
        case DataSetMetaTypes.CCS => validateConsensusReadSet(path)
        case DataSetMetaTypes.Contig => validateContigSet(path)
        case DataSetMetaTypes.AlignmentCCS => validateConsensusAlignmentSet(path)
        case x =>
          Left(InvalidDataSetError(s"Unsupported dataset type $x"))
      }
    } else {
      Left(InvalidDataSetError(s"Unable to find $path"))
    }
  }
}
