package com.pacbio.secondary.smrtlink.analysis.datasets.io

import java.nio.file.Path
import javax.xml.bind._

import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes

/**
  *
  * Created by mkocher on 5/17/15.
  *
  * I don't know how to make this generic.
  *
  * classOf[T] does NOT work due to erasure.
  */
object DataSetWriter {

  private def toMarshaller(jAXBContext: JAXBContext): Marshaller = {
    val jaxbMarshaller = jAXBContext.createMarshaller()
    // output pretty printed
    jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    jaxbMarshaller
  }

  def writeSubreadSet(dataset: SubreadSet, path: Path): SubreadSet = {
    toMarshaller(JAXBContext.newInstance(classOf[SubreadSetType]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeHdfSubreadSet(dataset: HdfSubreadSet, path: Path): HdfSubreadSet = {
    toMarshaller(JAXBContext.newInstance(classOf[HdfSubreadSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeReferenceSet(dataset: ReferenceSet, path: Path): ReferenceSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ReferenceSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeAlignmentSet(dataset: AlignmentSet, path: Path): AlignmentSet = {
    toMarshaller(JAXBContext.newInstance(classOf[AlignmentSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeBarcodeSet(dataset: BarcodeSet, path: Path): BarcodeSet = {
    toMarshaller(JAXBContext.newInstance(classOf[BarcodeSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeConsensusReadSet(dataset: ConsensusReadSet,
                            path: Path): ConsensusReadSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ConsensusReadSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeContigSet(dataset: ContigSet, path: Path): ContigSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ContigSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeConsensusAlignmentSet(dataset: ConsensusAlignmentSet,
                                 path: Path): ConsensusAlignmentSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ConsensusAlignmentSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeGmapReferenceSet(dataset: GmapReferenceSet,
                            path: Path): GmapReferenceSet = {
    toMarshaller(JAXBContext.newInstance(classOf[GmapReferenceSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeTranscriptSet(dataset: TranscriptSet, path: Path): TranscriptSet = {
    toMarshaller(JAXBContext.newInstance(classOf[TranscriptSet]))
      .marshal(dataset, path.toFile)
    dataset
  }

  def writeDataSet(dst: DataSetMetaTypes.DataSetMetaType,
                   dataset: DataSetType,
                   path: Path): DataSetType = {
    dst match {
      case DataSetMetaTypes.Subread =>
        writeSubreadSet(dataset.asInstanceOf[SubreadSet], path)
      case DataSetMetaTypes.HdfSubread =>
        writeHdfSubreadSet(dataset.asInstanceOf[HdfSubreadSet], path)
      case DataSetMetaTypes.Reference =>
        writeReferenceSet(dataset.asInstanceOf[ReferenceSet], path)
      case DataSetMetaTypes.Alignment =>
        writeAlignmentSet(dataset.asInstanceOf[AlignmentSet], path)
      case DataSetMetaTypes.CCS =>
        writeConsensusReadSet(dataset.asInstanceOf[ConsensusReadSet], path)
      case DataSetMetaTypes.AlignmentCCS =>
        writeConsensusAlignmentSet(dataset.asInstanceOf[ConsensusAlignmentSet],
                                   path)
      case DataSetMetaTypes.Contig =>
        writeContigSet(dataset.asInstanceOf[ContigSet], path)
      case DataSetMetaTypes.Barcode =>
        writeBarcodeSet(dataset.asInstanceOf[BarcodeSet], path)
      case DataSetMetaTypes.GmapReference =>
        writeGmapReferenceSet(dataset.asInstanceOf[GmapReferenceSet], path)
      case DataSetMetaTypes.Transcript =>
        writeTranscriptSet(dataset.asInstanceOf[TranscriptSet], path)
    }
  }

  def writeDataSet(dataset: DataSetType, path: Path): DataSetType = {
    val dst = DataSetMetaTypes.fromString(dataset.getMetaType()).getOrElse {
      throw new RuntimeException("Can't get dataset metatype")
    }
    writeDataSet(dst, dataset, path)
  }
}
