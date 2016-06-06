package com.pacbio.secondary.analysis.converters

import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.{UUID, Calendar}
import javax.xml.datatype.DatatypeFactory

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime

import collection.JavaConverters._
import collection.JavaConversions._
import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.analysis.constants.{FileTypes, GlobalConstants}
import com.pacbio.secondary.analysis.datasets._
import com.pacbio.secondary.analysis.legacy.{ReferenceContig, ReferenceEntry, ReferenceEntryIO, ReferenceInfoType}
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, ExternalResources, InputOutputDataType}
import com.pacificbiosciences.pacbiodatasets.Contigs.Contig
import com.pacificbiosciences.pacbiodatasets.{ContigSetMetadataType, Contigs, ReferenceSet}


/**
 * Convert RS-era Reference Info XML to ReferenceSet
 *
 * Created by mkocher on 9/26/15.
 */
object ReferenceInfoConverter extends LazyLogging {

  val REF_INFO_TO_DS_VERSION = "0.5.0"

  // Legacy -> SA3 era Index files
  val REF_LEGACY = Map(
    FileTypes.RS_I_SAM_INDEX -> FileTypes.I_SAM,
    FileTypes.RS_I_INDEXER -> FileTypes.I_INDEX,
    FileTypes.RS_I_FCI -> FileTypes.I_FCI,
    FileTypes.RS_I_SAW -> FileTypes.I_SAW)

  /**
   * Convert an old-style ReferenceEntry to a ReferenceDataset
   *
   * @param r Reference Entry IO
   * @return
   */
  def converter(r: ReferenceEntryIO): ReferenceDatasetIO = {
    val tags = List("converted", "reference")
    val name = s"${r.record.organism}"
    converter(r, name, tags)
  }

  /**
   *
   * Convert a Reference and Set required DataSet metadata
   *
   * @param r
   * @param name
   * @param tags
   * @return
   */
  def converter(r: ReferenceEntryIO, name: String, tags: Seq[String] = Nil): ReferenceDatasetIO = {

    val uuid = java.util.UUID.randomUUID()
    val comments = s"PacBio ReferenceSet for $name Organism ${r.record.organism}"
    val createdAt = DateTime.now().toString

    // These need to be computed from the contigs
    val nRecords = r.record.metadata.nContigs
    val totalLength = r.record.metadata.totalLength

    val metadata = DataSetMetaData(uuid, name, CommonConstants.DATASET_VERSION, createdAt, tags, comments, nRecords, totalLength)

    val supportedIndexTypes = Seq(FileTypes.RS_I_FCI, FileTypes.RS_I_INDEXER, FileTypes.RS_I_SAM_INDEX, FileTypes.RS_I_SAW).map(_.fileTypeId)
    // These are the 'raw' Index files parsed from the Reference Index XML file
    // Filtering only the 'supported' RS index types
    val indexFiles = r.indexFiles.map { i => DatasetIndexFile(i.indexType, i.path) }.filter(supportedIndexTypes contains _.indexType)
    val rds = ReferenceDataset(r.record.organism, r.record.ploidy, r.record.contigs, metadata)
    ReferenceDatasetIO(r.fastaFile, rds, indexFiles)

  }

  def convertReferenceInfoToDataSet(referenceInfoType: ReferenceInfoType): ReferenceSet = {

    // pacbio_dataset_subreadset-<yymmdd_HHmmssttt>
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toTimeStampName(n: String) = s"${n}_$timeStamp"

    // This is so clumsy
    val uuid = UUID.randomUUID()
    val createdAt = DatatypeFactory.newInstance().newXMLGregorianCalendar(new DateTime().toGregorianCalendar)
    val timeStampName = toTimeStampName("referenceset")
    val fastaTimeStampName = toTimeStampName("fasta")

    val metatype = FileTypes.DS_REFERENCE.fileTypeId
    val fastaMetaType = FileTypes.FASTA_REF.fileTypeId

    // Is this really not defined as a constant somewhere?
    val version = "3.0.1"

    val tags = "converted, reference"
    val name = referenceInfoType.getId
    val description = s"Converted Reference $name"

    val metadata = new ContigSetMetadataType()

    val totalLength = referenceInfoType.getContigs.getContig.map(_.getLength.toLong).sum
    val numRecords = referenceInfoType.getReference.getNumContigs.toInt

    val contigItems: Seq[Contig] = referenceInfoType.getContigs.getContig.map { c =>
      val contig =  new Contig()
      //FIXME need to double check what "Id" versus "header" usage here
      contig.setName(c.getId)
      contig.setDigest(c.getDigest.getValue)
      contig.setDescription(c.getDisplayName)
      contig.setLength(c.getLength)
      contig
    }

    val contigs = new Contigs()
    contigs.getContig.addAll(contigItems)

    metadata.setContigs(contigs)

    metadata.setNumRecords(referenceInfoType.getReference.getNumContigs.toInt)
    metadata.setTotalLength(totalLength)

    // These can both be null
    metadata.setOrganism(referenceInfoType.getOrganism.getName)
    metadata.setPloidy(referenceInfoType.getOrganism.getPloidy)

    val er = new ExternalResource()
    er.setCreatedAt(createdAt)
    er.setModifiedAt(createdAt)
    er.setMetaType(fastaMetaType)
    er.setName(s"Fasta $name")
    er.setUniqueId(UUID.randomUUID().toString)
    er.setTags(tags)
    er.setDescription(s"Converted with v$REF_INFO_TO_DS_VERSION Reference Name $name")
    er.setTimeStampedName(fastaTimeStampName)
    er.setResourceId(referenceInfoType.getReference.getFile.getValue)


    val legacyIndex = REF_LEGACY.map( {case (k, v) => k.fileTypeId -> v.fileTypeId})

    // Process and Set External Resources
    val indexFiles = referenceInfoType.getReference.getIndexFile.map { i =>
      val f = new InputOutputDataType()
      f.setUniqueId(UUID.randomUUID().toString)
      f.setTimeStampedName(toTimeStampName("index"))
      f.setResourceId(i.getValue)
      // Not great
      f.setMetaType(legacyIndex.getOrElse(i.getType, "PacBio.Index.IndexFile"))
      f
    }

    val fileIndices = new FileIndices()
    fileIndices.getFileIndex.addAll(indexFiles)
    er.setFileIndices(fileIndices)

    val externalResources = new ExternalResources()
    externalResources.getExternalResource.add(er)

    val rs = new ReferenceSet()
    rs.setVersion(version)
    rs.setMetaType(metatype)
    rs.setCreatedAt(createdAt)
    rs.setModifiedAt(createdAt)
    rs.setTimeStampedName(timeStampName)
    rs.setUniqueId(uuid.toString)
    rs.setName(name)
    rs.setDescription(description)
    rs.setTags(tags)
    rs.setDataSetMetadata(metadata)
    rs.setExternalResources(externalResources)
    rs
  }

  /**
   * This needs to be rewritten to use the XSD generated classes to write the ReferenceSet
   *
   * @param datasetIO
   * @param path
   * @return
   */
  def writeReferenceDataset(datasetIO: ReferenceDatasetIO, path: Path): ReferenceDatasetFileIO = {

    // RS-era reference.info.xml file formats
    def toMetaType(sx: String): String = {
      sx match {
        case FileTypes.RS_I_SAM_INDEX.fileTypeId => FileTypes.I_SAM.fileTypeId
        case FileTypes.RS_I_INDEXER.fileTypeId => FileTypes.I_INDEX.fileTypeId
        case FileTypes.RS_I_SAW.fileTypeId => FileTypes.I_SAW.fileTypeId
        case FileTypes.RS_I_FCI.fileTypeId => FileTypes.I_FCI.fileTypeId
        case x =>
          logger.warn(s"Unknown metadata type $x")
          x
      }
    }

    // FIXME. Is this consistent with Martin's docs?
    def toTsn(uuid: UUID) = s"pacbio_dataset_index-${uuid.toString}"

    def contigToXML(c: ReferenceContig) = <pbds:Contig Name={c.name} Description={c.description} Length={c.length.toString} Digest={c.md5}/>

    def indexFileToXML(f: DatasetIndexFile) = {
      val uuid = UUID.randomUUID()
        <pbbase:FileIndex UniqueId={uuid.toString} TimeStampedName={toTsn(uuid)} MetaType={toMetaType(f.indexType)} ResourceId={f.url}/>
    }

    val r = datasetIO.dataset
    val erUUID = UUID.randomUUID()

    val author = s"pbscala ${GlobalConstants.PB_SCALA_VERSION} reference_info_dataset_" + REF_INFO_TO_DS_VERSION

    val root =
      <pbds:ReferenceSet
      xmlns:pbbase="http://pacificbiosciences.com/PacBioBaseDataModel.xsd"
      xmlns:pbds="http://pacificbiosciences.com/PacBioDatasets.xsd"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      TimeStampedName="referenceset_150304_231155"
      MetaType={FileTypes.DS_REFERENCE.fileTypeId}
      Name={r.metadata.name}
      CreatedAt={r.metadata.createdAt}
      UniqueId={r.metadata.uuid.toString}
      Version={CommonConstants.DATASET_VERSION}
      Author={author}>
        <pbbase:ExternalResources>
          <pbbase:ExternalResource
          Name="First References FASTA"
          Description="Points to an example references FASTA file."
          MetaType={FileTypes.FASTA_REF.fileTypeId}
          ResourceId={datasetIO.fastaFile}
          UniqueId={erUUID.toString}
          TimeStampedName={toTsn(erUUID)}
          Tags="converted">
            <pbbase:FileIndices>
              {datasetIO.indexFiles.map(c => indexFileToXML(c))}
            </pbbase:FileIndices>
          </pbbase:ExternalResource>
        </pbbase:ExternalResources>
        <pbds:DataSetMetadata>
          <pbds:TotalLength>{r.metadata.totalLength}</pbds:TotalLength>
          <pbds:NumRecords>{r.metadata.numRecords}</pbds:NumRecords>
          <pbds:Organism>{r.organism}</pbds:Organism>
          <pbds:Ploidy>{r.ploidy}</pbds:Ploidy>
          <pbds:Contigs>
            {r.contigs.map(c => contigToXML(c))}
          </pbds:Contigs>
        </pbds:DataSetMetadata>
      </pbds:ReferenceSet>

    val p = new scala.xml.PrettyPrinter(80, 4)
    scala.xml.XML.save(path.toString, root, "UTF-8", true, null)

    ReferenceDatasetFileIO(path, datasetIO.fastaFile, datasetIO.dataset, datasetIO.indexFiles)
  }

  def convertReferenceInfoXMLToDataset(path: String, dsPath: Path): Either[DatasetConvertError, ReferenceDatasetIO] = {
    try {
      val r = ReferenceEntry.loadFrom(path)
      val ds = converter(r)
      writeReferenceDataset(ds, dsPath)
      Right(ds)
    } catch {
      case e: Exception =>
        Left(DatasetConvertError(e.getMessage))
    }
  }

}
