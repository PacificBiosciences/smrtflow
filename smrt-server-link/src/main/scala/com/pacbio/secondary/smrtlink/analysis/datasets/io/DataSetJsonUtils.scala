package com.pacbio.secondary.smrtlink.analysis.datasets.io

import java.io.{ByteArrayOutputStream,StringReader}
import javax.xml.transform.stream.StreamSource
import javax.xml.bind._

import org.eclipse.persistence.jaxb.JAXBContextFactory
import org.eclipse.persistence.jaxb.{MarshallerProperties,UnmarshallerProperties}

import spray.json._

import com.pacificbiosciences.pacbiodatasets._

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

  private def contextToUnmarshaller(jAXBContext: JAXBContext):Unmarshaller = {
    val junmarshaller = jAXBContext.createUnmarshaller()
    junmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false)
    junmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json")
    junmarshaller
  }

  private def contextToJson[A <: DataSetType](jAXBContext: JAXBContext, dataset: A): String = {
    val outStream = new ByteArrayOutputStream()
    contextToMarshaller(jAXBContext).marshal(dataset, outStream)
    outStream.toString
  }

  def referenceSetToJson(dataset: ReferenceSet) =
    contextToJson(JAXBContext.newInstance(classOf[ReferenceSet]), dataset)

  def referenceSetFromJson(json: String): ReferenceSet = {
    val ctx = JAXBContext.newInstance(classOf[ReferenceSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[ReferenceSet]).getValue().asInstanceOf[ReferenceSet]
  }

  def hdfSubreadSetToJson(dataset: HdfSubreadSet) =
    contextToJson(JAXBContext.newInstance(classOf[HdfSubreadSet]), dataset)

  def hdfSubreadSetFromJson(json: String): HdfSubreadSet = {
    val ctx = JAXBContext.newInstance(classOf[HdfSubreadSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[HdfSubreadSet]).getValue().asInstanceOf[HdfSubreadSet]
  }

  def subreadSetToJson(dataset: SubreadSet) =
    contextToJson(JAXBContext.newInstance(classOf[SubreadSet]), dataset)

  def subreadSetFromJson(json: String): SubreadSet = {
    val ctx = JAXBContext.newInstance(classOf[SubreadSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[SubreadSet]).getValue().asInstanceOf[SubreadSet]
  }

  def alignmentSetToJson(dataset: AlignmentSet) =
    contextToJson(JAXBContext.newInstance(classOf[AlignmentSet]), dataset)

  def alignmentSetFromJson(json: String): AlignmentSet = {
    val ctx = JAXBContext.newInstance(classOf[AlignmentSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[AlignmentSet]).getValue().asInstanceOf[AlignmentSet]
  }

  def barcodeSetToJson(dataset: BarcodeSet) =
    contextToJson(JAXBContext.newInstance(classOf[BarcodeSet]), dataset)

  def barcodeSetFromJson(json: String): BarcodeSet = {
    val ctx = JAXBContext.newInstance(classOf[BarcodeSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[BarcodeSet]).getValue().asInstanceOf[BarcodeSet]
  }

  def consensusSetToJson(dataset: ConsensusReadSet) =
    contextToJson(JAXBContext.newInstance(classOf[ConsensusReadSet]), dataset)

  def consensusSetFromJson(json: String): ConsensusReadSet = {
    val ctx = JAXBContext.newInstance(classOf[ConsensusReadSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[ConsensusReadSet]).getValue().asInstanceOf[ConsensusReadSet]
  }

  def consensusAlignmentSetToJson(dataset: ConsensusAlignmentSet) =
    contextToJson(JAXBContext.newInstance(classOf[ConsensusAlignmentSet]), dataset)

  def consensusAlignmentSetFromJson(json: String): ConsensusAlignmentSet = {
    val ctx = JAXBContext.newInstance(classOf[ConsensusAlignmentSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[ConsensusAlignmentSet]).getValue().asInstanceOf[ConsensusAlignmentSet]
  }

  def contigSetToJson(dataset: ContigSet) =
    contextToJson(JAXBContext.newInstance(classOf[ContigSet]), dataset)

  def contigSetFromJson(json: String): ContigSet = {
    val ctx = JAXBContext.newInstance(classOf[ContigSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[ContigSet]).getValue().asInstanceOf[ContigSet]
  }

  def gmapReferenceSetToJson(dataset: GmapReferenceSet) =
    contextToJson(JAXBContext.newInstance(classOf[GmapReferenceSet]), dataset)

  def gmapReferenceSetFromJson(json: String): GmapReferenceSet = {
    val ctx = JAXBContext.newInstance(classOf[GmapReferenceSet])
    contextToUnmarshaller(ctx).unmarshal(new StreamSource(new StringReader(json)), classOf[GmapReferenceSet]).getValue().asInstanceOf[GmapReferenceSet]
  }

}

trait DataSetJsonProtocols extends DefaultJsonProtocol {

  implicit object SubreadSetJsonFormat extends RootJsonFormat[SubreadSet] {
    def write(obj: SubreadSet): JsObject =
      DataSetJsonUtils.subreadSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): SubreadSet =
      DataSetJsonUtils.subreadSetFromJson(json.toString)
  }

  implicit object HdfSubreadSetJsonFormat extends RootJsonFormat[HdfSubreadSet] {
    def write(obj: HdfSubreadSet): JsObject =
      DataSetJsonUtils.hdfSubreadSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): HdfSubreadSet =
      DataSetJsonUtils.hdfSubreadSetFromJson(json.toString)
  }

  implicit object ReferenceSetJsonFormat extends RootJsonFormat[ReferenceSet] {
    def write(obj: ReferenceSet): JsObject =
      DataSetJsonUtils.referenceSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): ReferenceSet =
      DataSetJsonUtils.referenceSetFromJson(json.toString)
  }

  implicit object BarcodeSetJsonFormat extends RootJsonFormat[BarcodeSet] {
    def write(obj: BarcodeSet): JsObject =
      DataSetJsonUtils.barcodeSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): BarcodeSet =
      DataSetJsonUtils.barcodeSetFromJson(json.toString)
  }

  implicit object AlignmentSetJsonFormat extends RootJsonFormat[AlignmentSet] {
    def write(obj: AlignmentSet): JsObject =
      DataSetJsonUtils.alignmentSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): AlignmentSet =
      DataSetJsonUtils.alignmentSetFromJson(json.toString)
  }

  implicit object ConsensusReadSetJsonFormat extends RootJsonFormat[ConsensusReadSet] {
    def write(obj: ConsensusReadSet): JsObject =
      DataSetJsonUtils.consensusSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): ConsensusReadSet =
      DataSetJsonUtils.consensusSetFromJson(json.toString)
  }

  implicit object ConsensusAlignmentSetJsonFormat extends RootJsonFormat[ConsensusAlignmentSet] {
    def write(obj: ConsensusAlignmentSet): JsObject =
      DataSetJsonUtils.consensusAlignmentSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): ConsensusAlignmentSet =
      DataSetJsonUtils.consensusAlignmentSetFromJson(json.toString)
  }

  implicit object ContigSetJsonFormat extends RootJsonFormat[ContigSet] {
    def write(obj: ContigSet): JsObject =
      DataSetJsonUtils.contigSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): ContigSet =
      DataSetJsonUtils.contigSetFromJson(json.toString)
  }

  implicit object GmapReferenceSetJsonFormat extends RootJsonFormat[GmapReferenceSet] {
    def write(obj: GmapReferenceSet): JsObject =
      DataSetJsonUtils.gmapReferenceSetToJson(obj).parseJson.asJsObject
    def read(json: JsValue): GmapReferenceSet =
      DataSetJsonUtils.gmapReferenceSetFromJson(json.toString)
  }

}

object DataSetJsonProtocol extends DataSetJsonProtocols
