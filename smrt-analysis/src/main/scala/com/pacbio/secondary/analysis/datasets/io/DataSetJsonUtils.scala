package com.pacbio.secondary.analysis.datasets.io

import java.io.ByteArrayOutputStream
import javax.xml.bind._

import com.pacificbiosciences.pacbiodatasets._
import org.eclipse.persistence.jaxb.JAXBContextFactory
import org.eclipse.persistence.jaxb.MarshallerProperties

/**
 *
 * Created by mkocher on 6/7/15.
 */
object DataSetJsonUtils {

  private def contextToMarshaller(jAXBContext: JAXBContext):Marshaller = {
    //println(s"Context ${jAXBContext.getClass}")
    val jmarshaller = jAXBContext.createMarshaller()
    jmarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    jmarshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, false)
    jmarshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/json")
    jmarshaller
  }

  private def contextToJson[A <: DataSetType](jAXBContext: JAXBContext, dataset: A): String = {
    val outStream = new ByteArrayOutputStream()
    contextToMarshaller(jAXBContext).marshal(dataset, outStream)
    outStream.toString
  }

  def referenceSetToJson(dataset: ReferenceSet) =
    contextToJson(JAXBContext.newInstance(classOf[ReferenceSet]), dataset)

  def hdfSubreadSetToJson(dataset: HdfSubreadSet) =
    contextToJson(JAXBContext.newInstance(classOf[HdfSubreadSet]), dataset)

  def subreadSetToJson(dataset: SubreadSet) =
    contextToJson(JAXBContext.newInstance(classOf[SubreadSet]), dataset)

  def alignmentSetToJson(dataset: AlignmentSet) =
    contextToJson(JAXBContext.newInstance(classOf[AlignmentSet]), dataset)

  def barcodeSetToJson(dataset: BarcodeSet) =
    contextToJson(JAXBContext.newInstance(classOf[BarcodeSet]), dataset)

  def consensusSetToJson(dataset: ConsensusReadSet) =
    contextToJson(JAXBContext.newInstance(classOf[ConsensusReadSet]), dataset)

  def consensusAlignmentSetToJson(dataset: ConsensusAlignmentSet) =
    contextToJson(JAXBContext.newInstance(classOf[ConsensusAlignmentSet]), dataset)

  def contigSetToJson(dataset: ContigSet) =
    contextToJson(JAXBContext.newInstance(classOf[ContigSet]), dataset)

}
