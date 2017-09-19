package com.pacbio.secondary.smrtlink.models

import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

import scala.util.Try
import scala.collection.JavaConversions._

import org.joda.time.{DateTime => JodaDateTime}

import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetadataUtils

/**
  * Utils for converting DataSet XML-ized objects to "Service" DataSets
  *
  *
  * The Utils convert the {DataSetType} -> {DataSetType}ServiceSet
  *
  * Created by mkocher on 5/26/15.
  */
object Converters extends DataSetMetadataUtils {

  // Default values for dataset attributes/elements that are Not found or are valid
  val UNKNOWN = "unknown"
  val DEFAULT_VERSION = "0.0.0"
  val DEFAULT_TAGS = "converted"
  val DEFAULT_SAMPLE_NAME = UNKNOWN
  val DEFAULT_WELL_NAME = UNKNOWN
  val DEFAULT_RUN_NAME = UNKNOWN
  val DEFAULT_CONTEXT = UNKNOWN
  val DEFAULT_INST = UNKNOWN
  val DEFAULT_CELL_ID = UNKNOWN
  val DEFAULT_INST_CTL_VERSION = UNKNOWN
  val MULTIPLE_SAMPLES_NAME = "[multiple]"

  def toMd5(text: String): String =
    MessageDigest
      .getInstance("MD5")
      .digest(text.getBytes)
      .map("%02x".format(_))
      .mkString

  private def getNameOrDefault(names: Seq[String],
                               default: String = UNKNOWN): String = {
    if (names.length == 1) names.head
    else if (names.length > 1) MULTIPLE_SAMPLES_NAME
    else default
  }

  def convert(dataset: SubreadSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): SubreadServiceDataSet = {
    // this is not correct, but the timestamps are often written correctly
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt

    val dsUUID = UUID.fromString(dataset.getUniqueId)
    val md5 = toMd5(dataset.getUniqueId)

    val metadata = getCollectionsMetadata(dataset)

    //MK. I'm annoyed with all this null-mania datamodel nonsense. Wrapping every fucking thing in a Try Option
    // there's a more clever way to do this but I don't care.
    val name = Try { Option(dataset.getName).getOrElse(UNKNOWN) } getOrElse UNKNOWN
    val dsVersion = Try {
      Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    } getOrElse DEFAULT_VERSION
    val tags = Try { Option(dataset.getTags).getOrElse(DEFAULT_TAGS) } getOrElse DEFAULT_TAGS

    // This might not be correct. Should the description come from the Collection Metadata
    val comments = Try {
      Option(
        dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getDescription)
        .getOrElse(" ")
    } getOrElse " "

    // Plate Id doesn't exist, but keeping it so I don't have to update the db schema
    val cellIndex = Try {
      metadata.map(_.head.getCellIndex.toInt).getOrElse(-1)
    } getOrElse -1
    val wellName = metadata
      .map(m => Option(m.head.getWellSample.getWellName))
      .map(_.getOrElse(DEFAULT_WELL_NAME))
      .getOrElse(DEFAULT_WELL_NAME)
    val runName = metadata
      .map(m => Option(m.head.getRunDetails.getName))
      .map(_.getOrElse(DEFAULT_RUN_NAME))
      .getOrElse(DEFAULT_RUN_NAME)
    val metadataCreatedBy = Try {
      metadata.map(_.head.getRunDetails.getCreatedBy)
    } getOrElse None
    val contextId = Try {
      metadata
        .map(_.head.getContext)
        .getOrElse(DEFAULT_CONTEXT)
    } getOrElse DEFAULT_CONTEXT
    val instrumentName = Try {
      metadata
        .map(_.head.getInstrumentName)
        .getOrElse(DEFAULT_INST)
    } getOrElse DEFAULT_INST
    val instrumentControlVersion = Try {
      metadata
        .map(_.head.getInstCtrlVer)
        .getOrElse(DEFAULT_INST_CTL_VERSION)
    } getOrElse (DEFAULT_INST_CTL_VERSION)

    // This one is slightly messier because we need to handle the case of
    // multiple bio samples
    val bioSampleName = getNameOrDefault(getBioSampleNames(dataset))
    val barcodes = getDnaBarcodeNames(dataset)
    val dnaBarcodeName = {
      if (barcodes.size == 0) None
      else if (barcodes.size > 1) Some(MULTIPLE_SAMPLES_NAME)
      else Some(barcodes.head)
    }
    val wellSampleName = getNameOrDefault(getWellSampleNames(dataset))

    val cellId = Try {
      Option(
        dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getCellPac.getBarcode)
        .getOrElse(DEFAULT_CELL_ID)
    }.getOrElse(DEFAULT_CELL_ID)

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    SubreadServiceDataSet(
      -99,
      dsUUID,
      name,
      path.toAbsolutePath.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      dsVersion,
      comments,
      tags,
      md5,
      instrumentName,
      instrumentControlVersion,
      contextId,
      wellSampleName,
      wellName,
      bioSampleName,
      cellIndex,
      cellId,
      runName,
      createdBy.orElse(metadataCreatedBy),
      jobId,
      projectId,
      dnaBarcodeName,
      parentUuid = None
    )
  }

  def convert(dataset: HdfSubreadSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): HdfSubreadServiceDataSet = {
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt

    // Because of the how the schemas are defined, everything is wrapped in Try to default to a value if there's a
    // problem parsing an element or attribute
    val name = Option(dataset.getName).getOrElse(UNKNOWN)
    val dsVersion = Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    val tags = Try { dataset.getTags } getOrElse "converted"
    val wellSampleName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getName
    } getOrElse UNKNOWN
    val comments = Try {
      dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getDescription
    } getOrElse " "

    // Plate Id doesn't exist, but keeping it so I don't have to update the db schema
    val cellIndex = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getCellIndex.toInt
    } getOrElse -1
    val wellName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getWellName
    } getOrElse UNKNOWN
    val runName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getRunDetails.getName
    } getOrElse UNKNOWN
    val contextId = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getContext
    } getOrElse UNKNOWN
    val instrumentName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getInstrumentName
    } getOrElse UNKNOWN

    val bioSampleName = getNameOrDefault(getBioSampleNames(dataset))

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    HdfSubreadServiceDataSet(
      -99,
      UUID.fromString(dataset.getUniqueId),
      name,
      path.toAbsolutePath.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      dsVersion,
      comments,
      dataset.getTags,
      toMd5(dataset.getUniqueId),
      instrumentName,
      contextId,
      wellSampleName,
      wellName,
      bioSampleName,
      cellIndex,
      runName,
      createdBy,
      jobId,
      projectId
    )
  }

  def convert(dataset: ContigSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): ContigServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "contig dataset comments"

    val name = Option(dataset.getName).getOrElse(UNKNOWN)
    val dsVersion = Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    //val tags = dataset.getTags
    val tags = ""

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    ContigServiceDataSet(-99,
                         uuid,
                         name,
                         path.toFile.toString,
                         createdAt,
                         modifiedAt,
                         numRecords,
                         totalLength,
                         dsVersion,
                         comments,
                         tags,
                         toMd5(uuid.toString),
                         createdBy,
                         jobId,
                         projectId)
  }

  def convert(dataset: ReferenceSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): ReferenceServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "reference dataset comments"

    val name = Option(dataset.getName).getOrElse(UNKNOWN)
    val dsVersion = Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    //val tags = dataset.getTags
    val tags = ""

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    ReferenceServiceDataSet(
      -99,
      uuid,
      name,
      path.toFile.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      dsVersion,
      comments,
      tags,
      toMd5(uuid.toString),
      createdBy,
      jobId,
      projectId,
      dataset.getDataSetMetadata.getPloidy,
      dataset.getDataSetMetadata.getOrganism
    )
  }

  // FIXME way too much code duplication here
  def convert(dataset: GmapReferenceSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): GmapReferenceServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "reference dataset comments"

    //val tags = dataset.getTags
    val name = Option(dataset.getName).getOrElse(UNKNOWN)
    val dsVersion = Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    val tags = ""

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    GmapReferenceServiceDataSet(
      -99,
      uuid,
      name,
      path.toFile.toString,
      createdAt,
      modifiedAt,
      numRecords,
      totalLength,
      dsVersion,
      comments,
      tags,
      toMd5(uuid.toString),
      createdBy,
      jobId,
      projectId,
      dataset.getDataSetMetadata.getPloidy,
      dataset.getDataSetMetadata.getOrganism
    )
  }

  def convert(dataset: AlignmentSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): AlignmentServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "alignment dataset converted"

    //val tags = dataset.getTags
    val name = Option(dataset.getName).getOrElse(UNKNOWN)
    val dsVersion = Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    val tags = ""
    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    AlignmentServiceDataSet(-99,
                            uuid,
                            name,
                            path.toFile.toString,
                            createdAt,
                            modifiedAt,
                            numRecords,
                            totalLength,
                            dsVersion,
                            comments,
                            tags,
                            toMd5(uuid.toString),
                            createdBy,
                            jobId,
                            projectId)
  }

  def convert(dataset: ConsensusReadSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): ConsensusReadServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "ccs dataset converted"

    //val tags = dataset.getTags
    val name = Option(dataset.getName).getOrElse(UNKNOWN)
    val dsVersion = Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    val tags = ""
    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    ConsensusReadServiceDataSet(-99,
                                uuid,
                                name,
                                path.toFile.toString,
                                createdAt,
                                modifiedAt,
                                numRecords,
                                totalLength,
                                dsVersion,
                                comments,
                                tags,
                                toMd5(uuid.toString),
                                createdBy,
                                jobId,
                                projectId)
  }

  // FIXME consolidate with AlignmentSet implementation
  def convert(dataset: ConsensusAlignmentSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): ConsensusAlignmentServiceDataSet = {
    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val comments = "ccs alignment dataset converted"

    //val tags = dataset.getTags
    val name = Option(dataset.getName).getOrElse(UNKNOWN)
    val dsVersion = Option(dataset.getVersion).getOrElse(DEFAULT_VERSION)
    val tags = ""
    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    ConsensusAlignmentServiceDataSet(-99,
                                     uuid,
                                     name,
                                     path.toFile.toString,
                                     createdAt,
                                     modifiedAt,
                                     numRecords,
                                     totalLength,
                                     dsVersion,
                                     comments,
                                     tags,
                                     toMd5(uuid.toString),
                                     createdBy,
                                     jobId,
                                     projectId)
  }

  def convert(dataset: BarcodeSet,
              path: Path,
              createdBy: Option[String],
              jobId: Int,
              projectId: Int): BarcodeServiceDataSet = {

    val uuid = UUID.fromString(dataset.getUniqueId)
    // this is not correct
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    // There is no description at the root level. There's a description in the ExternalResource
    // MK. It's not clear to me what's going on here. If it's null, Barcode null imported is on several datasets
    val barcodeConstruction =
      Option(dataset.getDataSetMetadata.getBarcodeConstruction).getOrElse("")
    val comments = s"Barcode $barcodeConstruction imported"

    val tags = Try { Some(dataset.getTags).getOrElse("") }.getOrElse("")
    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords } getOrElse 0
    val totalLength = Try { dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

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
                          createdBy,
                          jobId,
                          projectId)

  }
}
