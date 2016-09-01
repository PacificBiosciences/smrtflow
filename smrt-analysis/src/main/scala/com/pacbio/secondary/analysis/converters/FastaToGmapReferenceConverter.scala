
package com.pacbio.secondary.analysis.converters

import java.nio.file.{Files, Path, Paths}
import java.io.{File,FileInputStream,FileOutputStream}
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.{UUID, Calendar}
import javax.xml.datatype.DatatypeFactory

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import spray.json._

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets._
import com.pacbio.secondary.analysis.externaltools.{CallGmapBuild, ExternalCmdFailure}
import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.analysis.datasets.io.DataSetWriter

import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{ContigSetMetadataType, Contigs, GmapReferenceSet}
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, InputOutputDataType, ExternalResources}

trait GmapDbProtocol extends DefaultJsonProtocol {

  // this might be better off somewhere else
  case class GmapDbInfo(name: String, timeStamp: String, dbPath: String)
  implicit val gmapDbInfoFormat = jsonFormat3(GmapDbInfo)

}

object GmapReferenceConverter extends FastaConverterBase[GmapReferenceSet, ContigSetMetadataType] with LazyLogging with GmapDbProtocol {

  protected val baseName: String = "gmap reference"
  protected val dsName: String = "GmapReferenceSet"
  protected val programName: String = "fasta-to-gmap-reference"
  protected val metatype: String = FileTypes.DS_GMAP_REF.fileTypeId
  protected val fastaMetatype: String = FileTypes.FASTA_REF.fileTypeId

  def generateGmapDb(fastaPath: Path, name: String, outputDir: Path): Either[ExternalCmdFailure, GmapDbInfo] = {
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    CallGmapBuild.run(fastaPath, outputDir) match {
      case Right(dbPath) => Right(GmapDbInfo(name, timeStamp, dbPath.toAbsolutePath.toString))
      case Left(err) => Left(err)
    }
  }

  override protected def setMetadata(ds: GmapReferenceSet, metadata: ContigSetMetadataType): Unit = ds.setDataSetMetadata(metadata)

  def createGmapReferenceSet(fastaPath: Path,
                             refMetaData: ContigsMetaData,
                             dbInfo: GmapDbInfo,
                             name: String,
                             organism: Option[String],
                             ploidy: Option[String],
                             outputDir: Path): GmapReferenceSet = {
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toTimeStampName(n: String) = s"${n}_$timeStamp"
    val dbFile = Paths.get(dbInfo.dbPath).resolve("gmap_build.json").toAbsolutePath.toString
    val dbOut = new PrintWriter(new File(dbFile))
    dbOut.write(dbInfo.toJson.toString)
    dbOut.close

    val metadata = composeMetaData(refMetaData)
    organism match {
      case Some(o) => metadata.setOrganism(o)
      case _ => null
    }
    ploidy match {
      case Some(p) => metadata.setPloidy(p)
      case _ => null
    }    
    val ds = composeDataSet(fastaPath, name, outputDir, metadata)
    val er = ds.getExternalResources.getExternalResource.get(0)

    val db = new ExternalResource()
    db.setCreatedAt(er.getCreatedAt)
    db.setModifiedAt(er.getCreatedAt)
    db.setMetaType(FileTypes.JSON.fileTypeId)
    db.setName("GMAP DB")
    db.setName(s"Fasta $name")
    db.setUniqueId(UUID.randomUUID().toString)
    db.setTags("gmap")
    db.setDescription(s"Created by $programName")
    db.setResourceId(outputDir.relativize(Paths.get(dbFile)).toString)
    
    val fastaResources = new ExternalResources()
    fastaResources.getExternalResource.add(db)
    er.setExternalResources(fastaResources)

    ds
  }

  def createDataset(name: String, organism: Option[String],
                    ploidy: Option[String], fastaPath: Path, outputDir: Path):
                    Either[DatasetConvertError, GmapReferenceSet] = {
    PacBioFastaValidator(fastaPath) match {
      case Left(x) => Left(DatasetConvertError(s"${x}"))
      case Right(refMetaData) => generateGmapDb(fastaPath, name, outputDir) match {
        case Right(dbInfo) => {
          val rs = createGmapReferenceSet(fastaPath, refMetaData, dbInfo, name,
                                          organism, ploidy, outputDir)
          Right(rs)
        }
        case Left(x) => Left(DatasetConvertError(s"${x}"))
      }
    }
  }

  def apply(name: String, fastaPath: Path, outputDir: Path,
            organism: Option[String], ploidy: Option[String],
            inPlace: Boolean = false):
            Either[DatasetConvertError, GmapReferenceSetIO] = {
    val target = setupTargetDir(name, fastaPath, outputDir, inPlace)
    createDataset(target.name, organism, ploidy, target.fastaPath,
                  target.dataDir) match {
      case Right(rs) => {
        DataSetWriter.writeGmapReferenceSet(rs, target.dsFile)
        Right(GmapReferenceSetIO(rs, target.dsFile))
      }
      case Left(err) => Left(err)
    }
  }
}
