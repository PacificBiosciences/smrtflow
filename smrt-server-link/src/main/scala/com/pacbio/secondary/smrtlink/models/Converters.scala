package com.pacbio.secondary.smrtlink.models

import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

import scala.util.Try
import scala.collection.JavaConverters._
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
  // some others are defined in DataSetMetadataUtils
  val DEFAULT_VERSION = "0.0.0"
  val DEFAULT_TAGS = "converted"
  val DEFAULT_SAMPLE_NAME = UNKNOWN
  val DEFAULT_WELL_NAME = UNKNOWN
  val DEFAULT_RUN_NAME = UNKNOWN
  val DEFAULT_CONTEXT = UNKNOWN
  val DEFAULT_INST = UNKNOWN
  val DEFAULT_CELL_ID = UNKNOWN
  val DEFAULT_INST_CTL_VERSION = UNKNOWN

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

  private def getParentDataSetId(dataset: ReadSetType): Option[UUID] =
    Option(dataset.getDataSetMetadata.getProvenance).flatMap { provenance =>
      Option(provenance.getParentDataSet).map { ds =>
        UUID.fromString(ds.getUniqueId)
      }
    }

  /**
    * First try to extract the created at time
    * from the XML, if that doesn't work or is
    * malformed, then use the last modified
    * timestamp of the file.
    *
    */
  private def getCreatedAt[T <: DataSetType](
      ds: T,
      path: Path): Option[JodaDateTime] = {
    Try {
      // These APIs are terrible to work with.
      // This might not be the recommended way to do this.
      val xs = ds.getCreatedAt
      val c = xs.toGregorianCalendar
      new JodaDateTime(c.getTimeInMillis)
    }.recover {
      case _ =>
        // I'm not sure this is the best method
        new JodaDateTime(path.toFile.lastModified())
    }
  }.toOption

  private case class M(uuid: UUID,
                       name: String,
                       createdAt: JodaDateTime,
                       modifiedAt: JodaDateTime,
                       dsVersion: String,
                       tags: String,
                       md5: String,
                       comments: String)

  private def convertMetadata[T <: DataSetType](
      ds: T,
      defaultName: String = UNKNOWN,
      defaultTags: String = "",
      defaultComment: String = "",
      defaultCreatedAt: Option[JodaDateTime] = None): M = {

    val now = JodaDateTime.now()
    // The timestamp model still needs to be clarified
    //FIXME(mpkocher) This should be added to the model
    val importedAt = now
    val createdAt = defaultCreatedAt.getOrElse(now)
    val modifiedAt = createdAt

    val uuid = UUID.fromString(ds.getUniqueId)
    val name = Option(ds.getName).getOrElse(defaultName)
    val dsVersion = Option(ds.getVersion).getOrElse(DEFAULT_VERSION)
    val tags = Option(ds.getTags).getOrElse(defaultTags)

    val comment = Option(ds.getDescription).getOrElse(defaultComment)

    M(uuid,
      name,
      createdAt,
      modifiedAt,
      dsVersion,
      tags,
      toMd5(uuid.toString),
      comment)
  }

  def getNumRecordsTotalLength[T <: DataSetMetadataType](m: T): (Int, Long) = {
    val numRecords: Int = Try { m.getNumRecords } getOrElse 0
    val totalLength = Try { m.getTotalLength } getOrElse 0L
    (numRecords, totalLength)
  }

  def convertSubreadSet(dataset: SubreadSet,
                        path: Path,
                        createdBy: Option[String],
                        jobId: Int,
                        projectId: Int): SubreadServiceDataSet = {

    // XXX this is only the first collection!  we do not always want to use
    // the values we extract from it as representative of the entire dataset
    val metadata = getCollectionsMetadata(dataset).headOption

    // This might not be correct. Should the description come from the Collection Metadata
    val comments = Try {
      Option(
        dataset.getDataSetMetadata.getBioSamples.getBioSample.asScala.head.getDescription)
        .getOrElse(" ")
    } getOrElse " "

    // Plate Id doesn't exist, but keeping it so I don't have to update the db schema
    val cellIndex = Try {
      metadata.map(_.getCellIndex.toInt).getOrElse(-1)
    } getOrElse -1
    val wellName =
      metadata
        .flatMap(m => Option(m.getWellSample).map(_.getWellName))
        .getOrElse(DEFAULT_WELL_NAME)
    val runName = metadata
      .flatMap(m => Option(m.getRunDetails).map(_.getName))
      .map(s => if (s == null) DEFAULT_RUN_NAME else s)
      .getOrElse(DEFAULT_RUN_NAME)
    val metadataCreatedBy =
      metadata.flatMap(m => Option(m.getRunDetails).map(_.getCreatedBy))
    val contextId =
      metadata
        .flatMap(m => Option(m.getContext))
        .getOrElse(DEFAULT_CONTEXT)
    val instrumentName =
      metadata
        .flatMap(m => Option(m.getInstrumentName))
        .getOrElse(DEFAULT_INST)
    val instrumentControlVersion =
      metadata
        .flatMap(m => Option(m.getInstCtrlVer))
        .getOrElse(DEFAULT_INST_CTL_VERSION)

    // This one is slightly messier because we need to handle the case of
    // multiple bio samples
    val bioSampleName = getNameOrDefault(getBioSampleNames(dataset))
    val barcodes = getDnaBarcodeNames(dataset)
    val dnaBarcodeName: Option[String] = {
      if (barcodes.isEmpty) None
      else if (barcodes.size > 1) Some(MULTIPLE_SAMPLES_NAME)
      else Some(barcodes.head)
    }
    val wellSampleName = getNameOrDefault(getWellSampleNames(dataset))

    val cellId = Try {
      Option(
        dataset.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head.getCellPac.getBarcode)
        .getOrElse(DEFAULT_CELL_ID)
    }.getOrElse(DEFAULT_CELL_ID)

    val parentUuid = getParentDataSetId(dataset)

    val m = convertMetadata(dataset,
                            defaultTags = "converted",
                            defaultComment = comments,
                            defaultCreatedAt = getCreatedAt(dataset, path))
    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)

    SubreadServiceDataSet(
      -99,
      m.uuid,
      m.name,
      path.toAbsolutePath.toString,
      m.createdAt,
      m.modifiedAt,
      numRecords,
      totalLength,
      m.dsVersion,
      m.comments,
      m.tags,
      m.md5,
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
      parentUuid = parentUuid,
      isActive = true
    )
  }

  def convertHdfSubreadSet(dataset: HdfSubreadSet,
                           path: Path,
                           createdBy: Option[String],
                           jobId: Int,
                           projectId: Int): HdfSubreadServiceDataSet = {

    val wellSampleName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head.getWellSample.getName
    } getOrElse UNKNOWN

    val comments = Try {
      dataset.getDataSetMetadata.getBioSamples.getBioSample.asScala.head.getDescription
    } getOrElse " "

    // Plate Id doesn't exist, but keeping it so I don't have to update the db schema
    val cellIndex = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head.getCellIndex.toInt
    } getOrElse -1
    val wellName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head.getWellSample.getWellName
    } getOrElse UNKNOWN
    val runName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head.getRunDetails.getName
    } getOrElse UNKNOWN
    val contextId = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head.getContext
    } getOrElse UNKNOWN
    val instrumentName = Try {
      dataset.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head.getInstrumentName
    } getOrElse UNKNOWN

    val bioSampleName = getNameOrDefault(getBioSampleNames(dataset))

    val m = convertMetadata(dataset,
                            defaultTags = "converted",
                            defaultComment = comments,
                            defaultCreatedAt = getCreatedAt(dataset, path))
    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)

    HdfSubreadServiceDataSet(
      -99,
      m.uuid,
      m.name,
      path.toAbsolutePath.toString,
      m.createdAt,
      m.modifiedAt,
      numRecords,
      totalLength,
      m.dsVersion,
      m.comments,
      m.tags,
      m.md5,
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

  def convertContigSet(dataset: ContigSet,
                       path: Path,
                       createdBy: Option[String],
                       jobId: Int,
                       projectId: Int): ContigServiceDataSet = {

    val m =
      convertMetadata(dataset,
                      defaultComment = "contig dataset comments",
                      defaultCreatedAt = getCreatedAt(dataset, path))
    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)

    ContigServiceDataSet(-99,
                         m.uuid,
                         m.name,
                         path.toFile.toString,
                         m.createdAt,
                         m.modifiedAt,
                         numRecords,
                         totalLength,
                         m.dsVersion,
                         m.comments,
                         m.tags,
                         m.md5,
                         createdBy,
                         jobId,
                         projectId)
  }

  def convertReferenceSet(dataset: ReferenceSet,
                          path: Path,
                          createdBy: Option[String],
                          jobId: Int,
                          projectId: Int): ReferenceServiceDataSet = {
    val m =
      convertMetadata(dataset,
                      defaultComment = "reference dataset comments",
                      defaultCreatedAt = getCreatedAt(dataset, path))
    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)

    ReferenceServiceDataSet(
      -99,
      m.uuid,
      m.name,
      path.toFile.toString,
      m.createdAt,
      m.modifiedAt,
      numRecords,
      totalLength,
      m.dsVersion,
      m.comments,
      m.tags,
      m.md5,
      createdBy,
      jobId,
      projectId,
      dataset.getDataSetMetadata.getPloidy,
      dataset.getDataSetMetadata.getOrganism
    )
  }

  // FIXME way too much code duplication here
  def convertGmapReferenceSet(dataset: GmapReferenceSet,
                              path: Path,
                              createdBy: Option[String],
                              jobId: Int,
                              projectId: Int): GmapReferenceServiceDataSet = {

    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)
    val m = convertMetadata[GmapReferenceSet](
      dataset,
      defaultComment = "reference dataset comments",
      defaultCreatedAt = getCreatedAt(dataset, path))

    GmapReferenceServiceDataSet(
      -99,
      m.uuid,
      m.name,
      path.toFile.toString,
      m.createdAt,
      m.modifiedAt,
      numRecords,
      totalLength,
      m.dsVersion,
      m.comments,
      m.tags,
      m.md5,
      createdBy,
      jobId,
      projectId,
      dataset.getDataSetMetadata.getPloidy,
      dataset.getDataSetMetadata.getOrganism
    )
  }

  def convertAlignmentSet(dataset: AlignmentSet,
                          path: Path,
                          createdBy: Option[String],
                          jobId: Int,
                          projectId: Int): AlignmentServiceDataSet = {

    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)
    val m = convertMetadata[AlignmentSet](dataset,
                                          defaultComment =
                                            "alignment dataset converted")

    AlignmentServiceDataSet(-99,
                            m.uuid,
                            m.name,
                            path.toFile.toString,
                            m.createdAt,
                            m.modifiedAt,
                            numRecords,
                            totalLength,
                            m.dsVersion,
                            m.comments,
                            m.tags,
                            m.md5,
                            createdBy,
                            jobId,
                            projectId)
  }

  def convertConsensusReadSet(dataset: ConsensusReadSet,
                              path: Path,
                              createdBy: Option[String],
                              jobId: Int,
                              projectId: Int): ConsensusReadServiceDataSet = {
    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)
    val m = convertMetadata(dataset,
                            defaultComment = "ccs dataset converted",
                            defaultCreatedAt = getCreatedAt(dataset, path))
    ConsensusReadServiceDataSet(-99,
                                m.uuid,
                                m.name,
                                path.toFile.toString,
                                m.createdAt,
                                m.modifiedAt,
                                numRecords,
                                totalLength,
                                m.dsVersion,
                                m.comments,
                                m.tags,
                                m.md5,
                                createdBy,
                                jobId,
                                projectId)
  }

  // FIXME consolidate with AlignmentSet implementation
  def convertConsensusAlignmentSet(
      dataset: ConsensusAlignmentSet,
      path: Path,
      createdBy: Option[String],
      jobId: Int,
      projectId: Int): ConsensusAlignmentServiceDataSet = {

    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)
    val m = convertMetadata(dataset,
                            defaultComment = "ccs alignment dataset converted",
                            defaultCreatedAt = getCreatedAt(dataset, path))

    ConsensusAlignmentServiceDataSet(-99,
                                     m.uuid,
                                     m.name,
                                     path.toFile.toString,
                                     m.createdAt,
                                     m.modifiedAt,
                                     numRecords,
                                     totalLength,
                                     m.dsVersion,
                                     m.comments,
                                     m.tags,
                                     m.md5,
                                     createdBy,
                                     jobId,
                                     projectId)
  }

  def convertTranscriptSet(dataset: TranscriptSet,
                           path: Path,
                           createdBy: Option[String],
                           jobId: Int,
                           projectId: Int): TranscriptServiceDataSet = {

    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)
    val m =
      convertMetadata(dataset,
                      defaultComment = "transcript dataset converted",
                      defaultCreatedAt = getCreatedAt(dataset, path))

    TranscriptServiceDataSet(-99,
                             m.uuid,
                             m.name,
                             path.toFile.toString,
                             m.createdAt,
                             m.modifiedAt,
                             numRecords,
                             totalLength,
                             m.dsVersion,
                             m.comments,
                             m.tags,
                             m.md5,
                             createdBy,
                             jobId,
                             projectId)
  }

  def convertBarcodeSet(dataset: BarcodeSet,
                        path: Path,
                        createdBy: Option[String],
                        jobId: Int,
                        projectId: Int): BarcodeServiceDataSet = {

    // There is no description at the root level. There's a description in the ExternalResource
    // MK. It's not clear to me what's going on here. If it's null, Barcode null imported is on several datasets
    val barcodeConstruction =
      Option(dataset.getDataSetMetadata.getBarcodeConstruction).getOrElse("")

    val (numRecords, totalLength) = getNumRecordsTotalLength(
      dataset.getDataSetMetadata)

    val m = convertMetadata(dataset,
                            "BarcodeSet",
                            defaultComment =
                              s"Barcode $barcodeConstruction imported",
                            defaultCreatedAt = getCreatedAt(dataset, path))
    // The BC construction should be stored here, but that would require a schema change. Putting it in the comments for now
    BarcodeServiceDataSet(-1,
                          m.uuid,
                          m.name,
                          path.toFile.toString,
                          m.createdAt,
                          m.modifiedAt,
                          numRecords,
                          totalLength,
                          m.dsVersion,
                          m.comments,
                          m.tags,
                          m.md5,
                          createdBy,
                          jobId,
                          projectId)

  }
}
