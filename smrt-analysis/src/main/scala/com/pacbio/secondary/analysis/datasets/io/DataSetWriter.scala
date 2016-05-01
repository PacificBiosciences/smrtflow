package com.pacbio.secondary.analysis.datasets.io

import java.nio.file.Path
import javax.xml.bind._

import com.pacificbiosciences.pacbiodatasets._

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
    toMarshaller(JAXBContext.newInstance(classOf[SubreadSetType])).marshal(dataset, path.toFile)
    dataset
  }

  def writeHdfSubreadSet(dataset: HdfSubreadSet, path: Path): HdfSubreadSet = {
    toMarshaller(JAXBContext.newInstance(classOf[HdfSubreadSet])).marshal(dataset, path.toFile)
    dataset
  }

  def writeReferenceSet(dataset: ReferenceSet, path: Path): ReferenceSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ReferenceSet])).marshal(dataset, path.toFile)
    dataset
  }

  def writeAlignmentSet(dataset: AlignmentSet, path: Path): AlignmentSet = {
    toMarshaller(JAXBContext.newInstance(classOf[AlignmentSet])).marshal(dataset, path.toFile)
    dataset
  }

  def writeBarcodeSet(dataset: BarcodeSet, path: Path): BarcodeSet = {
    toMarshaller(JAXBContext.newInstance(classOf[BarcodeSet])).marshal(dataset, path.toFile)
    dataset
  }

  def writeDataSet(dataset: ConsensusReadSet, path: Path): ConsensusReadSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ConsensusReadSet])).marshal(dataset, path.toFile)
    dataset
  }

  def writeContigSet(dataset: ContigSet, path: Path): ContigSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ContigSet])).marshal(dataset, path.toFile)
    dataset
  }

  def writeConsensusAlignmentSet(dataset: ConsensusAlignmentSet, path: Path): ConsensusAlignmentSet = {
    toMarshaller(JAXBContext.newInstance(classOf[ConsensusAlignmentSet])).marshal(dataset, path.toFile)
    dataset
  }
}
