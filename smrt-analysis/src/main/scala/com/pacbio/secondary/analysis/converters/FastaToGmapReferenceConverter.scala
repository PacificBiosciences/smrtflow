
package com.pacbio.secondary.analysis.converters

import java.nio.file.{Files, Path}
import java.text.SimpleDateFormat
import java.util.{UUID, Calendar}
import javax.xml.datatype.DatatypeFactory

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import spray.json._

import scala.collection.mutable

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets._
import com.pacbio.secondary.analysis.externaltools.{CallGmapBuild, ExternalCmdFailure, ExternalToolsUtils}
//import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils
import com.pacbio.common.models.{Constants => CommonConstants}

import com.pacbio.secondary.analysis.referenceUploader.ReposUtils
import com.pacificbiosciences.pacbiodatasets.Contigs.Contig
import com.pacificbiosciences.pacbiodatasets.{ContigSetMetadataType, Contigs, GmapReferenceSet}
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, InputOutputDataType, ExternalResources}

trait GmapDbProtocol extends DefaultJsonProtocol {

  // this might be better off somewhere else
  case class GmapDbInfo(name: String, timeStamp: String, dbPath: String)
  implicit val gmapDbInfoFormat = jsonFormat3(GmapDbInfo)

}

object GmapReferenceConverter extends LazyLogging with GmapDbProtocol with FastaConverterBase with ExternalToolsUtils {

  def generateGmapDb(fastaFile: Path, name: String, outputDir: Path): Either[ExternalCmdFailure, GmapDbInfo] = {
    val sanitizedName = ReposUtils.nameToFileName(name)
    var dbDir = outputDir.resolve(sanitizedName)
    if (Files.exists(dbDir)) throw DatasetConvertError(s"The directory ${dbDir} already exists -please remove it or specify an alternate output directory or reference name.")
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    CallGmapBuild.run(fastaFile, name, outputDir) match {
      case Right(dbPath) => Right(GmapDbInfo(sanitizedName, timeStamp, dbPath.toString))
      case Left(err) => Left(err)
    }
  }

  def constructGmapReferenceSet(fastaFile: Path, dbInfo: GmapDbInfo,
                                name: String, organism: String,
                                ploidy: String): GmapReferenceSet = {
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toTimeStampName(n: String) = s"${n}_$timeStamp"

    validateFastaFile(fastaFile) match {
      case Left(x) => throw new Error(s"Can't create GmapReferenceSet: ${x}")
      case Right(contigs) => {
        val nrecords = contigs.length
        val totalLength = contigs.foldLeft(0)((m, n) => m + n.length)
    
        // This is so clumsy
        val uuid = UUID.randomUUID()
        val createdAt = DatatypeFactory.newInstance().newXMLGregorianCalendar(new DateTime().toGregorianCalendar)
        val timeStampName = toTimeStampName("gmapreferenceset")
        val fastaTimeStampName = toTimeStampName("fasta")
    
        val metatype = FileTypes.DS_REFERENCE.fileTypeId
        val fastaMetaType = FileTypes.FASTA_REF.fileTypeId
    
        // Is this really not defined as a constant somewhere?
    
        val tags = "converted, reference"
        val description = s"Converted Reference $name"
    
        val metadata = new ContigSetMetadataType()
        /*val contigItems = Seq[Contig]()
    
        val contigs = new Contigs()
        contigs.getContig.addAll(contigItems)
    
        metadata.setContigs(contigs)*/
    
        metadata.setNumRecords(nrecords)
        metadata.setTotalLength(totalLength)
    
        // These can both be null
        metadata.setOrganism(organism)
        metadata.setPloidy(ploidy)
    
        val er = new ExternalResource()
        er.setCreatedAt(createdAt)
        er.setModifiedAt(createdAt)
        er.setMetaType(fastaMetaType)
        er.setName(s"Fasta $name")
        er.setUniqueId(UUID.randomUUID().toString)
        er.setTags(tags)
        er.setDescription("Converted with fasta-to-gmap-reference")
        er.setTimeStampedName(fastaTimeStampName)
        er.setResourceId(fastaFile.toAbsolutePath.toString)
    
        val db = new ExternalResource()
        er.setCreatedAt(createdAt)
        er.setModifiedAt(createdAt)
        er.setMetaType(FileTypes.JSON.fileTypeId)
        er.setName("GMAP DB")
        er.setName(s"Fasta $name")
        er.setUniqueId(UUID.randomUUID().toString)
        er.setTags(tags)
        er.setDescription("Created by fasta-to-gmap-reference")
    
        val fastaResources = new ExternalResources()
        fastaResources.getExternalResource.add(db)
        er.setExternalResources(fastaResources)
    
        val externalResources = new ExternalResources()
        externalResources.getExternalResource.add(er)
    
        val rs = new GmapReferenceSet()
        rs.setVersion(CommonConstants.DATASET_VERSION)
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
    }
  }
}
