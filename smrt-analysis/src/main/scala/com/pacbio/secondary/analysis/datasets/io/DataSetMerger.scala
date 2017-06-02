package com.pacbio.secondary.analysis.datasets.io

import java.nio.file.Path
import java.util.UUID
import javax.xml.datatype.DatatypeFactory

import com.pacbio.common.models.Constants
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, IndexedDataType, ExternalResources}
import com.pacificbiosciences.pacbiocollectionmetadata.Collections
import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiosampleinfo.BioSamples
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import collection.JavaConversions._
import scala.util.Failure


trait DataSetMerger extends LazyLogging{
  val VERSION = "0.3.0"
  val TAG_MERGED = "merged"

  /**
   * Merge a SubreadSet and dataset by using the Unique Id of the Dataset and Merging External Resources using
   * ResourceId as the hash key.
   *
   * The type should be DataSetType, but the XSD model defines numRecords and totalLength on DataSetMetadataType.
   *
   * @param datasets
   * @param name
   * @tparam T
   * @return
   */
  def merge[T <: DataSetType](
        datasets: Seq[T],
        name: String,
        newDataSet: T): T = {

    val createdAt = DatatypeFactory.newInstance().newXMLGregorianCalendar(new DateTime().toGregorianCalendar)

    val uds = datasets.map(x => (x.getUniqueId, x)).toMap.values.toList

    val ds = newDataSet

    ds.setName(name)
    ds.setVersion(Constants.DATASET_VERSION)
    ds.setUniqueId(UUID.randomUUID().toString)
    ds.setMetaType(datasets.head.getMetaType)
    ds.setTags(datasets.head.getTags)
    // Is this using iso8601 ?
    ds.setCreatedAt(createdAt)

    ds.setDescription(s"Merged dataset from ${uds.length} files from ${datasets.length} original files using DatasetMerger $VERSION")

    // Get All Unique External Resources using ResourceId as the key
    val externalResources = uds.flatMap(_.getExternalResources.getExternalResource.toList)
      .map(x => (x.getResourceId, x)).toMap.values

    // Overwrite all External Resources
    val exs = new ExternalResources()
    externalResources.foreach { r =>
      exs.getExternalResource.add(r)
    }

    val dss = new DataSetType.DataSets()
    datasets.foreach { ds =>
      dss.getDataSet.add(ds)
    }

    ds.setExternalResources(exs)
    ds.setDataSets(dss)
    ds
  }

  /**
   * Merge the Collections and BioSamples (SummaryStats not implemented)
   *
   *
   * Unique-ness of Objects:
   * CollectionMetadata (Context)
   * BioSamples (UniqueId)
   *
   * @param ms
   * @return
   */
  private def mergeReadSetMetadata(ms: Seq[ReadSetMetadataType]): ReadSetMetadataType = {

    // merge Collections by Context (i.e., movie id)
    val uniqueCollectionMetadata = ms
      .filter(_.getCollections != null)
      .flatMap(_.getCollections.getCollectionMetadata.map(mx => (mx.getContext, mx)).toMap.values)

    val collections = new Collections()
    collections.getCollectionMetadata.addAll(uniqueCollectionMetadata)

    // merge Bio samples
    val uniqueBioSampleList = ms.flatMap(m => Option(m.getBioSamples)).flatMap(_.getBioSample).toList.map(bs => (bs.getUniqueId, bs)).toMap.values
    // there's a duplication of this definition in pacbioinfo ?
    val bioSamples = new BioSamples()
    bioSamples.getBioSample.addAll(uniqueBioSampleList)

    //
    val metadata = new ReadSetMetadataType()
    metadata.setCollections(collections)
    metadata.setBioSamples(bioSamples)

    metadata
  }

  /**
    * Merge subclasses for ReadSet type.
    *
    * See comments above about the DataSet data model which creates headaches with num records and total length
    *
    * @param datasets Non-empty list of DataSets
    * @param name     DataSet name
    * @tparam T Read Type
    * @return
    */
  private def mergeReadSets[T <: ReadSetType](
        datasets: Seq[T],
        name: String,
        newDataSet: T): T = {
    val uuid = UUID.randomUUID()
    val createdAt = DatatypeFactory.newInstance().newXMLGregorianCalendar(new DateTime().toGregorianCalendar)

    val uds = datasets.map(x => (x.getUniqueId, x)).toMap.values.toList

    // Merge common metadata
    val numRecords = uds.map(_.getDataSetMetadata.getNumRecords).sum
    val totalLength = uds.map(_.getDataSetMetadata.getTotalLength).sum
    val description = s"Merged dataset from ${uds.length} files from ${datasets.length} original files using DatasetMerger $VERSION"

    // Overwrite the initial settings
    val ds = newDataSet

    val mergedReadSetMetadata = mergeReadSetMetadata(uds.map(u => u.getDataSetMetadata))

    // How tags are defined is not clearly defined in the DataSet spec.
    // This is a bit tragic to try to guess the format based on
    // a comma separated format that will trim white space on each tag
    def parseTags(sx: Option[String]): Set[String] = {
      sx.map(a => a.split(",").map(_.trim).filter(_.nonEmpty).toSet)
          .getOrElse(Set.empty[String])
    }

    val tags:Set[String] = datasets.flatMap(x => parseTags(Option(x.getTags))).toSet ++ Set(TAG_MERGED)

    // Update custom metadata for ReadSet
    ds.setDataSetMetadata(mergedReadSetMetadata)

    ds.setUniqueId(uuid.toString)
    ds.setName(name)
    ds.setMetaType(datasets.head.getMetaType)
    ds.setTags(tags.reduceLeftOption(_ + "," + _).getOrElse(""))
    ds.setCreatedAt(createdAt)
    ds.setVersion(Constants.DATASET_VERSION)

    ds.getDataSetMetadata.setNumRecords(numRecords)
    ds.getDataSetMetadata.setTotalLength(totalLength)

    ds.setDescription(description)

    // Get All Unique External Resources using ResourceId as the key
    val externalResources = uds.filter(_.getExternalResources != null)
      .flatMap(_.getExternalResources.getExternalResource)
      .map(x => (x.getResourceId, x)).toMap.values

    // Overwrite all External Resources
    val exs = new ExternalResources()
    ds.setExternalResources(exs)
    ds.getExternalResources.getExternalResource.addAll(externalResources)

    val dss = new DataSetType.DataSets()
    ds.setDataSets(dss)
    ds.getDataSets.getDataSet.addAll(datasets)

    ds
  }

  def mergeHdfSubreadSets(datasets: Seq[HdfSubreadSet], name: String): HdfSubreadSet = mergeReadSets(datasets, name, new HdfSubreadSet())
  def mergeSubreadSets(datasets: Seq[SubreadSet], name: String): SubreadSet = mergeReadSets(datasets, name, new SubreadSet())

  def mergeAlignmentSets(datasets: Seq[AlignmentSet], name: String): AlignmentSet = merge[AlignmentSet](datasets, name, new AlignmentSet())


  private def mergeDataSetPaths[T <: DataSetType](
      loader: Path => T,
      merger: (Seq[T], String) => T): (Seq[Path], String) => T = { (paths, name) =>
    merger(paths.map(px => loader(px)), name)
  }

  def mergeSubreadSetPaths(paths: Seq[Path], name: String = "merged-subreadset"): SubreadSet =
    mergeDataSetPaths[SubreadSet](DataSetLoader.loadAndResolveSubreadSet, mergeSubreadSets)(paths, name)

  def mergeHdfSubreadSetPaths(paths: Seq[Path], name: String = "merged-hdfsubreadset"): HdfSubreadSet =
    mergeDataSetPaths[HdfSubreadSet](DataSetLoader.loadAndResolveHdfSubreadSet, mergeHdfSubreadSets)(paths, name)

  def mergeAlignmentSetPaths(paths: Seq[Path], name: String = "merged-alignmentset"): AlignmentSet =
    mergeDataSetPaths[AlignmentSet](DataSetLoader.loadAlignmentSet, mergeAlignmentSets)(paths, name)

  /**
   * Merge datasets and write to an output file
   *
   * @param merger Merging Func
   * @param writer Writing Func
   * @tparam T output DataSet
   * @return
   */
  private def mergeDataSetTo[T <: DataSetType](
      merger: (Seq[Path], String) => T,
      writer: (T, Path) => T): (Seq[Path], String, Path) => T = { (paths, name, outputPath) =>
    writer(merger(paths, name), outputPath)
  }

  def mergeSubreadSetPathsTo(paths: Seq[Path], name: String, outputPath: Path): SubreadSet =
    mergeDataSetTo(mergeSubreadSetPaths, DataSetWriter.writeSubreadSet)(paths, name, outputPath)

  def mergeHdfSubreadSetPathsTo(paths: Seq[Path], name: String, outputPath: Path): HdfSubreadSet =
    mergeDataSetTo(mergeHdfSubreadSetPaths, DataSetWriter.writeHdfSubreadSet)(paths, name, outputPath)

  def mergeAlignmentSetPathsTo(paths: Seq[Path], name: String, outputPath: Path): AlignmentSet = {
    logger.warn("Partial support for merging AlignmentSet")
    mergeDataSetTo(mergeAlignmentSetPaths, DataSetWriter.writeAlignmentSet)(paths, name, outputPath)
  }

}

/**
 * Simple Merging of Datasets
 *
 * Created by mkocher on 5/15/15.
 */
object DataSetMerger extends DataSetMerger with LazyLogging
