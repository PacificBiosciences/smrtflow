package com.pacbio.secondary.smrtlink.analysis.datasets.io

import java.net.URI
import java.nio.file.{Path, Paths}
import javax.xml.bind.JAXBContext

import com.typesafe.scalalogging.LazyLogging

import collection.JavaConverters._
import scala.language.postfixOps
import scala.language.higherKinds

import com.pacbio.secondary.smrtlink.analysis.datasets
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  BarcodeSetIO,
  ConsensusReadSetIO,
  ContigSetIO,
  DataSetIO,
  DataSetType => _,
  _
}
import com.pacificbiosciences.pacbiobasedatamodel.{
  ExternalResource,
  ExternalResources
}
import com.pacificbiosciences.pacbiodatasets.{DataSetType, _}

/**
  * Load datasets from XML
  *
  * Created by mkocher on 5/15/15.
  */
object DataSetLoader extends LazyLogging {

  private def toUnMarshaller(context: JAXBContext, path: Path) = {
    val unmarshaller = context.createUnmarshaller()
    unmarshaller.unmarshal(path.toFile)
  }

  private def toAbsolute(px: Path, root: Path): Path = {
    if (px.isAbsolute) px else root.resolve(px).toAbsolutePath
  }

  private def resourceToAbsolutePath(resource: String, rootDir: Path): Path = {
    val uri = URI.create(resource.replaceAll(" ", "%20"))
    val path =
      if (uri.getScheme == null) Paths.get(resource) else Paths.get(uri)
    val realPath =
      if (path.isAbsolute) path.toAbsolutePath
      else rootDir.resolve(path).normalize().toAbsolutePath
    realPath
  }

  /**
    * Resolve ResourceId, externalResources and file indices
    *
    * @param externalResource
    * @param path
    * @return
    */
  def resolveExternalResource(externalResource: ExternalResource,
                              path: Path): ExternalResource = {

    if (externalResource.getFileIndices != null) {
      val indexFiles =
        externalResource.getFileIndices.getFileIndex.asScala.map { x =>
          val px = resourceToAbsolutePath(x.getResourceId, path)
          x.setResourceId(px.toString)
          x
        }

      val uniqueIndexFiles =
        (indexFiles.map(f => f.getResourceId -> f) toMap).values.toList.asJava

      val fs = new FileIndices()
      fs.getFileIndex.addAll(uniqueIndexFiles)

      externalResource.setFileIndices(fs)
    }

    val rexsOpt = Option(externalResource.getExternalResources)

    rexsOpt match {
      case Some(rexs) =>
        if (rexs.getExternalResource.asScala.nonEmpty) {
          val resolvedExs = resolveExternalResources(rexs, path)
          externalResource.setExternalResources(resolvedExs)
          rexs
        }
      case _ => None
    }
    externalResource.setResourceId(resourceToAbsolutePath(
      externalResource.getResourceId,
      path).toAbsolutePath.toString)
    externalResource
  }

  def resolveExternalResources(resources: ExternalResources,
                               path: Path): ExternalResources = {

    if (resources != null) {
      val exs = new ExternalResources()
      val rx = resources.getExternalResource.asScala
        .map(x => resolveExternalResource(x, path))
        .toList

      exs.getExternalResource.addAll(rx.asJava)
      exs
    } else {
      resources
    }
  }

  /**
    * Resolve relative Paths
    *
    * @param ds      DataSet
    * @param rootDir root Path of resources (relative to the original DataSet XML)
    * @tparam T
    * @return
    */
  def resolveDataSet[T <: DataSetType](ds: T, rootDir: Path): T = {

    val resolvedExternalResources =
      resolveExternalResources(ds.getExternalResources, rootDir)
    ds.setExternalResources(resolvedExternalResources)
    ds
  }

  def loadReferenceSet(path: Path): ReferenceSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[ReferenceSet]), path)
      .asInstanceOf[ReferenceSet]

  def loadAndResolveReferenceSet(path: Path): ReferenceSet =
    resolveDataSet(loadReferenceSet(path), path.toAbsolutePath.getParent)

  def loadReferenceSetIO(path: Path): ReferenceSetIO =
    ReferenceSetIO(loadReferenceSet(path), path)

  def loadSubreadSet(path: Path): SubreadSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[SubreadSet]), path)
      .asInstanceOf[SubreadSet]

  def loadAndResolveSubreadSet(path: Path): SubreadSet =
    resolveDataSet(loadSubreadSet(path), path.toAbsolutePath.getParent)

  def loadSubreadSetIO(path: Path) =
    SubreadSetIO(loadAndResolveSubreadSet(path), path)

  def loadHdfSubreadSet(path: Path): HdfSubreadSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[HdfSubreadSet]), path)
      .asInstanceOf[HdfSubreadSet]

  def loadAndResolveHdfSubreadSet(path: Path) =
    resolveDataSet(loadHdfSubreadSet(path), path.toAbsolutePath.getParent)

  def loadHdfSubreadSetIO(path: Path) =
    HdfSubreadSetIO(loadHdfSubreadSet(path), path)

  def loadAlignmentSet(path: Path): AlignmentSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[AlignmentSet]), path)
      .asInstanceOf[AlignmentSet]

  def loadAndResolveAlignmentSet(path: Path) =
    resolveDataSet(loadAlignmentSet(path), path.toAbsolutePath.getParent)

  def loadAlignmentSetIO(path: Path) =
    AlignmentSetIO(loadAlignmentSet(path), path)

  def loadBarcodeSet(path: Path): BarcodeSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[BarcodeSet]), path)
      .asInstanceOf[BarcodeSet]

  def loadAndResolveBarcodeSet(path: Path) =
    resolveDataSet(loadBarcodeSet(path), path.toAbsolutePath.getParent)

  def loadBarcodeSetIO(path: Path) = BarcodeSetIO(loadBarcodeSet(path), path)

  def loadConsensusReadSet(path: Path): ConsensusReadSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[ConsensusReadSet]), path)
      .asInstanceOf[ConsensusReadSet]

  def loadAndResolveConsensusReadSet(path: Path) =
    resolveDataSet(loadConsensusReadSet(path), path.toAbsolutePath.getParent)

  def loadConsensusReadSetIO(path: Path) =
    ConsensusReadSetIO(loadConsensusReadSet(path), path)

  def loadConsensusAlignmentSet(path: Path): ConsensusAlignmentSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[ConsensusAlignmentSet]),
                   path).asInstanceOf[ConsensusAlignmentSet]

  def loadAndResolveConsensusAlignmentSet(path: Path) =
    resolveDataSet(loadConsensusAlignmentSet(path),
                   path.toAbsolutePath.getParent)

  def loadConsensusAlignmentSetIO(path: Path) =
    ConsensusAlignmentSetIO(loadConsensusAlignmentSet(path), path)

  def loadContigSet(path: Path): ContigSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[ContigSet]), path)
      .asInstanceOf[ContigSet]

  def loadAndResolveContigSet(path: Path) =
    resolveDataSet(loadContigSet(path), path.toAbsolutePath.getParent)

  def loadContigSetIO(path: Path) = ContigSetIO(loadContigSet(path), path)

  def loadGmapReferenceSet(path: Path): GmapReferenceSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[GmapReferenceSet]), path)
      .asInstanceOf[GmapReferenceSet]

  def loadAndResolveGmapReferenceSet(path: Path): GmapReferenceSet =
    resolveDataSet(loadGmapReferenceSet(path), path.toAbsolutePath.getParent)

  def loadGmapReferenceSetIO(path: Path): GmapReferenceSetIO =
    GmapReferenceSetIO(loadGmapReferenceSet(path), path)

  def loadTranscriptSet(path: Path): TranscriptSet =
    toUnMarshaller(JAXBContext.newInstance(classOf[TranscriptSet]), path)
      .asInstanceOf[TranscriptSet]

  def loadAndResolveTranscriptSet(path: Path): TranscriptSet =
    resolveDataSet(loadTranscriptSet(path), path.toAbsolutePath.getParent)

  def loadTranscriptSetIO(path: Path): TranscriptSetIO =
    TranscriptSetIO(loadTranscriptSet(path), path)

  def loadType(dst: DataSetMetaTypes.DataSetMetaType,
               input: Path): DataSetType = {
    dst match {
      case DataSetMetaTypes.Subread => loadSubreadSet(input)
      case DataSetMetaTypes.HdfSubread => loadHdfSubreadSet(input)
      case DataSetMetaTypes.Reference => loadReferenceSet(input)
      case DataSetMetaTypes.Alignment => loadAlignmentSet(input)
      case DataSetMetaTypes.CCS => loadConsensusReadSet(input)
      case DataSetMetaTypes.AlignmentCCS => loadConsensusAlignmentSet(input)
      case DataSetMetaTypes.Contig => loadContigSet(input)
      case DataSetMetaTypes.Barcode => loadBarcodeSet(input)
      case DataSetMetaTypes.GmapReference => loadGmapReferenceSet(input)
      case DataSetMetaTypes.Transcript => loadTranscriptSet(input)
    }
  }
}

object ImplicitDataSetLoader {

  import DataSetLoader._

  abstract class DataSetLoader[T <: DataSetType] {
    def load(path: Path): T
  }

  implicit object SubreadSetLoader extends DataSetLoader[SubreadSet] {
    def load(path: Path) = loadSubreadSet(path)
  }

  implicit object HdfSubreadSetLoader extends DataSetLoader[HdfSubreadSet] {
    def load(path: Path) = loadHdfSubreadSet(path)
  }

  implicit object AlignmentSetLoader extends DataSetLoader[AlignmentSet] {
    def load(path: Path) = loadAlignmentSet(path)
  }

  implicit object ConsensusAlignmentSetLoader
      extends DataSetLoader[ConsensusAlignmentSet] {
    def load(path: Path) = loadConsensusAlignmentSet(path)
  }

  implicit object ReferenceSetLoader extends DataSetLoader[ReferenceSet] {
    def load(path: Path) = loadReferenceSet(path)
  }

  implicit object BarcodeSetLoader extends DataSetLoader[BarcodeSet] {
    def load(path: Path) = loadBarcodeSet(path)
  }

  implicit object ConsensusReadSetLoader
      extends DataSetLoader[ConsensusReadSet] {
    def load(path: Path) = loadConsensusReadSet(path)
  }

  implicit object ContigSetLoader extends DataSetLoader[ContigSet] {
    def load(path: Path) = loadContigSet(path)
  }

  implicit object GmapReferenceSetLoader
      extends DataSetLoader[GmapReferenceSet] {
    def load(path: Path) = loadGmapReferenceSet(path)
  }

  implicit object TranscriptSetLoader extends DataSetLoader[TranscriptSet] {
    def load(path: Path) = loadTranscriptSet(path)
  }

  def loader[T <: DataSetType](path: Path)(implicit lx: DataSetLoader[T]) =
    lx.load(path)

  def loaderAndResolve[T <: DataSetType](path: Path)(
      implicit lx: DataSetLoader[T]) =
    resolveDataSet[T](lx.load(path), path.toAbsolutePath.getParent)

  def loaderAndResolveType(dst: DataSetMetaTypes.DataSetMetaType,
                           input: Path): DataSetType = {
    dst match {
      case DataSetMetaTypes.Subread => loaderAndResolve[SubreadSet](input)
      case DataSetMetaTypes.HdfSubread =>
        loaderAndResolve[HdfSubreadSet](input)
      case DataSetMetaTypes.Reference => loaderAndResolve[ReferenceSet](input)
      case DataSetMetaTypes.Alignment => loaderAndResolve[AlignmentSet](input)
      case DataSetMetaTypes.CCS => loaderAndResolve[ConsensusReadSet](input)
      case DataSetMetaTypes.AlignmentCCS =>
        loaderAndResolve[ConsensusAlignmentSet](input)
      case DataSetMetaTypes.Contig => loaderAndResolve[ContigSet](input)
      case DataSetMetaTypes.Barcode => loaderAndResolve[BarcodeSet](input)
      case DataSetMetaTypes.GmapReference =>
        loaderAndResolve[GmapReferenceSet](input)
      case DataSetMetaTypes.Transcript =>
        loaderAndResolve[TranscriptSet](input)
    }
  }

}

object ImplicitDataSetIOLoader {

  import DataSetLoader._

  abstract class DataSetIOLoader[T <: DataSetIO] {
    def load(path: Path): T
  }

  implicit object SubreadSetIOLoader extends DataSetIOLoader[SubreadSetIO] {
    def load(path: Path): SubreadSetIO = loadSubreadSetIO(path)
  }

  implicit object HdfSubreadSetIOLoader
      extends DataSetIOLoader[HdfSubreadSetIO] {
    def load(path: Path) = loadHdfSubreadSetIO(path)
  }

  implicit object AlignmentSetIOLoader
      extends DataSetIOLoader[AlignmentSetIO] {
    def load(path: Path) = loadAlignmentSetIO(path)
  }

  implicit object ConsensusAlignmentSetIOLoader
      extends DataSetIOLoader[ConsensusAlignmentSetIO] {
    def load(path: Path) = loadConsensusAlignmentSetIO(path)
  }

  implicit object ReferenceSetIOLoader
      extends DataSetIOLoader[ReferenceSetIO] {
    def load(path: Path) = loadReferenceSetIO(path)
  }

  implicit object BarcodeSetIOLoader extends DataSetIOLoader[BarcodeSetIO] {
    def load(path: Path) = loadBarcodeSetIO(path)
  }

  implicit object ConsensusReadSetIOLoader
      extends DataSetIOLoader[ConsensusReadSetIO] {
    def load(path: Path) = loadConsensusReadSetIO(path)
  }

  implicit object ContigSetIOLoader extends DataSetIOLoader[ContigSetIO] {
    def load(path: Path) = loadContigSetIO(path)
  }

  implicit object GmapReferenceSetIOLoader
      extends DataSetIOLoader[GmapReferenceSetIO] {
    def load(path: Path) = loadGmapReferenceSetIO(path)
  }

  def loader[T <: DataSetIO](path: Path)(implicit lx: DataSetIOLoader[T]) =
    lx.load(path)

  // This needs to used higher Kinded types to access the specific DataSet type from the DataSet IO wrapper?
//  def loaderAndResolve[A <: DataSetIO](path: Path)(implicit lx: DataSetIOLoader[A]) =
//    resolveDataSet[A.T](lx.load(path), path.toAbsolutePath.getParent)

}
