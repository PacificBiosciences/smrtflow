package com.pacbio.secondary.smrtlink.models

import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

import scala.collection.JavaConversions._
import com.pacificbiosciences.pacbiodatasets._
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.Try

/**
 * Utils for converting DataSet XML-ized objects to "Service" DataSets
 *
 *
 * The Utils convert the {DataSetType} -> {DataSetType}ServiceSet
 *
 * Created by mkocher on 5/26/15.
 */
object Converters {

  // Default values for dataset attributes/elements that are Not found or are valid
  val UNKNOWN = "unknown"
  val DEFAULT_VERSION = "0.0.0"
  val DEFAULT_TAGS = "converted"
  val DEFAULT_SAMPLE_NAME = UNKNOWN
  val DEFAULT_WELL_NAME = UNKNOWN
  val DEFAULT_RUN_NAME = UNKNOWN
  val DEFAULT_CONTEXT = UNKNOWN
  val DEFAULT_INST = UNKNOWN
  val DEFAULT_BSAMPLE_NAME = UNKNOWN

  def toMd5(text: String): String = MessageDigest.getInstance("MD5").digest(text.getBytes).map("%02x".format(_)).mkString

  def convert(dataset: SubreadSet, path: Path, userId: Int, jobId: Int, projectId: Int): SubreadServiceDataSet = {
    // this is not correct, but the timestamps are often written correctly
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt

    val dsUUID = UUID.fromString(dataset.getUniqueId)
    val md5 = toMd5(dataset.getUniqueId)

    //MK. I'm annoyed with all this null-mania datamodel nonsense. Wrapping every fucking thing in a Try Option
    // there's a more clever way to do this but I don't care.
    val name = Try { Option(dataset.getName).getOrElse(UNKNOWN) } getOrElse UNKNOWN
    val dsVersion = Try { Option(dataset.getVersion).getOrElse(DEFAULT_VERSION) } getOrElse DEFAULT_VERSION
    val tags = Try { Option(dataset.getTags).getOrElse(DEFAULT_TAGS)} getOrElse DEFAULT_TAGS

    val wellSampleName = Try { Option(dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getName).getOrElse(DEFAULT_SAMPLE_NAME) } getOrElse DEFAULT_SAMPLE_NAME
    // This might not be correct. Should the description come from the Collection Metadata
    val comments = Try { Option(dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getDescription).getOrElse(" ") } getOrElse " "

    // Plate Id doesn't exist, but keeping it so I don't have to update the db schema
    val cellIndex = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getCellIndex.toInt } getOrElse -1
    val wellName = Try { Option(dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getWellName).getOrElse(DEFAULT_WELL_NAME) } getOrElse DEFAULT_WELL_NAME
    val runName = Try { Option(dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getRunDetails.getName).getOrElse(DEFAULT_RUN_NAME) } getOrElse DEFAULT_RUN_NAME
    val contextId = Try { Option(dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getContext).getOrElse(DEFAULT_CONTEXT) } getOrElse DEFAULT_CONTEXT
    val instrumentName = Try { Option(dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getInstrumentName).getOrElse(DEFAULT_INST) } getOrElse DEFAULT_INST

    val bioSampleName = Try { Option(dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getName).getOrElse(DEFAULT_BSAMPLE_NAME) } getOrElse DEFAULT_BSAMPLE_NAME

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords} getOrElse 0
    val totalLength = Try {dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    SubreadServiceDataSet(-99,
      dsUUID,
      name,
      path.toAbsolutePath.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      dsVersion,
      comments, tags,
      md5,
      instrumentName,
      contextId,
      wellSampleName,
      wellName,
      bioSampleName,
      cellIndex,
      runName,
      userId,
      jobId,
      projectId)
  }

  def convert(dataset: HdfSubreadSet, path: Path, userId: Int, jobId: Int, projectId: Int): HdfSubreadServiceDataSet = {
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt

    // Because of the how the schemas are defined, everything is wrapped in Try to default to a value if there's a
    // problem parsing an element or attribute
    val name = Try {dataset.getName} getOrElse UNKNOWN
    val dsVersion = Try {dataset.getVersion} getOrElse "0.0.0"
    val tags = Try {dataset.getTags} getOrElse "converted"
    val wellSampleName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getName } getOrElse UNKNOWN
    val comments = Try { dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getDescription  } getOrElse " "

    // Plate Id doesn't exist, but keeping it so I don't have to update the db schema
    val cellIndex = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getCellIndex.toInt } getOrElse -1
    val wellName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getWellName } getOrElse UNKNOWN
    val runName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getRunDetails.getName } getOrElse UNKNOWN
    val contextId = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getContext } getOrElse UNKNOWN
    val instrumentName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getInstrumentName } getOrElse UNKNOWN

    val bioSampleName = Try { dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getName } getOrElse UNKNOWN

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords} getOrElse 0
    val totalLength = Try {dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    HdfSubreadServiceDataSet(-99, UUID.fromString(dataset.getUniqueId), dataset.getName,
      path.toAbsolutePath.toString,
      createdAt, modifiedAt,
      numRecords,
      totalLength,
      dataset.getVersion,
      comments, dataset.getTags,
      toMd5(dataset.getUniqueId),
      instrumentName, contextId, wellSampleName, wellName, bioSampleName, cellIndex, runName, userId, jobId, projectId)
  }
  def convert(dataset: ReferenceSet, path: Path, userId: Int, jobId: Int, projectId: Int): ReferenceServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "reference dataset comments"

    //val tags = dataset.getTags
    val tags = ""

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try {dataset.getDataSetMetadata.getTotalLength} getOrElse 0L

    ReferenceServiceDataSet(-99,
      uuid,
      dataset.getName,
      path.toFile.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      dataset.getVersion,
      comments,
      tags, toMd5(uuid.toString), userId, jobId, projectId,
      dataset.getDataSetMetadata.getPloidy,
      dataset.getDataSetMetadata.getOrganism)
  }

  def convert(dataset: AlignmentSet, path: Path, userId: Int, jobId: Int, projectId: Int): AlignmentServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "alignment dataset converted"

    //val tags = dataset.getTags
    val tags = ""
    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try {dataset.getDataSetMetadata.getTotalLength} getOrElse 0L

    AlignmentServiceDataSet(-99,
      uuid,
      dataset.getName,
      path.toFile.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      dataset.getVersion,
      comments,
      tags, toMd5(uuid.toString), userId, jobId, projectId)
  }

  def convert(dataset: BarcodeSet, path: Path, userId: Int, jobId: Int, projectId: Int): BarcodeServiceDataSet = {

    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    // There is no description at the root level. There's a description in the ExternalResource
    val comments = s"Barcode ${dataset.getDataSetMetadata.getBarcodeConstruction} imported"

    val tags = Try { Some(dataset.getTags).getOrElse("") }.getOrElse("")
    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try {dataset.getDataSetMetadata.getTotalLength} getOrElse 0L

    val name = Option(dataset.getName).getOrElse("BarcodeSet")
    val version = Option(dataset.getVersion).getOrElse("Unknown")
    val md5 = toMd5(uuid.toString)

    // The BC construction should be stored here, but that would require a schema change. Putting it in the comments for now
    BarcodeServiceDataSet(-1,
      uuid,
      name,
      path.toAbsolutePath.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      version,
      comments,
      tags,
      md5,
      userId,
      jobId,
      projectId)

  }
}