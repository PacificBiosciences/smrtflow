package com.pacbio.secondary.analysis.datasets

import java.nio.file.Path
import java.util.UUID

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.constants.FileTypes.DataSetBaseType
import com.pacbio.secondary.analysis.jobs.UUIDJsonProtocol
import com.pacificbiosciences.pacbiodatasets.{SubreadSet, HdfSubreadSet, AlignmentSet, BarcodeSet, ConsensusReadSet, ConsensusAlignmentSet, ContigSet, ReferenceSet}
import com.pacificbiosciences.pacbiodatasets.{DataSetType => XmlDataSetType}


import spray.json._
import DefaultJsonProtocol._


/**
 * Core DataSet Types. This should be consolidated with FileTypes
 */
object DataSetMetaTypes {

  val BASE_PREFIX = "PacBio.DataSet"

  trait DataSetMetaType {
    val fileType: DataSetBaseType
    override def toString = fileType.fileTypeId
    def shortName: String

    def dsId = fileType.fileTypeId
  }

  case object Subread extends DataSetMetaType {
    final val fileType = FileTypes.DS_SUBREADS
    override def shortName = "subreads"
  }

  case object HdfSubread extends DataSetMetaType {
    final val fileType = FileTypes.DS_HDF_SUBREADS
    override def shortName = "hdfsubreads"
  }

  case object Alignment extends DataSetMetaType {
    final val fileType = FileTypes.DS_ALIGNMENTS
    override def shortName = "alignments"
  }

  case object Barcode extends DataSetMetaType {
    final val fileType = FileTypes.DS_BARCODE
    override def shortName = "barcodes"
  }

  case object CCS extends DataSetMetaType {
    final val fileType = FileTypes.DS_CCS
    override def shortName = "ccsreads"
  }

  case object AlignmentCCS extends DataSetMetaType {
    final val fileType = FileTypes.DS_CCS_ALIGNMENTS
    override def shortName = "ccsalignments"
  }

  case object Contig extends DataSetMetaType {
    final val fileType = FileTypes.DS_CONTIG
    override def shortName = "contigs"
  }

  case object Reference extends DataSetMetaType {
    final val fileType = FileTypes.DS_REFERENCE
    override def shortName = "references"
  }

  // FIXME. The order is important. Will reuse this in the db
  val ALL = Set(Subread, HdfSubread, Alignment, Barcode, CCS, Contig, Reference, AlignmentCCS)

  // This is for backward compatiblity
  def typeToIdString(x: DataSetMetaType) = x.toString

  /**
   * Convert DataSet 'shortname' to DataSet MetaType.
   * (Should probably sync up with Martin to potentially push this into pbcommand for consistency with the Python code)
   *
   * @param shortName
   * @return
   */
  def fromShortName(shortName: String): Option[DataSetMetaType] = {
    ALL.map(x => (x.shortName, x)).toMap.get(shortName)
  }

  /**
   * Convert PacBio full DataSet Id to DataSetMetaType
   *
   * @param dsType full id
   * @return
   */
  def toDataSetType(dsType: String): Option[DataSetMetaType] = {
    ALL.map(x => (typeToIdString(x), x)).toMap.get(dsType)
  }

}

// Small General Container for Dataset
case class DataSetRecord(
    uuid: UUID,
    datasetType: DataSetMetaTypes.DataSetMetaType,
    path: Path)

// Thin Container to Describe a general dataset type
// This should be updated to use the DataSetMetaType as the id
case class DataSetType(
    id: String,
    name: String,
    description: String)

case class DataSetMetaData(
    uuid: java.util.UUID,
    name: String,
    version: String,
    createdAt: String,
    tags: Seq[String],
    comments: String,
    numRecords: Int,
    totalLength: Int)

case class DatasetIndexFile(indexType: String, url: String)


trait DataSetMetaDataProtocol extends DefaultJsonProtocol with UUIDJsonProtocol {

  implicit val dataSetMetaDataFormat = jsonFormat8(DataSetMetaData)
  implicit val dataSetTypeFormat = jsonFormat3(DataSetType)
  implicit val dataSetIndexFile = jsonFormat2(DatasetIndexFile)

}

// General IO DataSet models. To Validate the DataSet, you need the path to XML file
// External Resources in DataSet can have paths that are defined Relative to the DataSet XML file
// Creating an IO container class

trait DataSetIO {
  type T <: XmlDataSetType
  val dataset: T
  val path: Path
}

case class SubreadSetIO(dataset: SubreadSet, path: Path) extends DataSetIO { type T = SubreadSet }
case class HdfSubreadSetIO(dataset: HdfSubreadSet, path: Path) extends DataSetIO { type T =HdfSubreadSet }
case class ReferenceSetIO(dataset: ReferenceSet, path: Path) extends DataSetIO { type T = ReferenceSet }
case class AlignmentSetIO(dataset: AlignmentSet, path: Path) extends DataSetIO { type T = AlignmentSet }
case class BarcodeSetIO(dataset: BarcodeSet, path: Path) extends DataSetIO { type T =BarcodeSet }
case class ConsensusReadSetIO(dataset: ConsensusReadSet, path: Path) extends DataSetIO { type T = ConsensusReadSet }
case class ConsensusAlignmentSetIO(dataset: ConsensusAlignmentSet, path: Path) extends DataSetIO { type T =ConsensusAlignmentSet }
case class ContigSetIO(dataset: ContigSet, path: Path) extends DataSetIO { type T = ContigSet }
