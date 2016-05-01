package com.pacbio.secondary.analysis.legacy

import java.nio.file.{Files, Path, Paths}

import com.pacbio.secondary.analysis.PacBioFileReader
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.constants.FileTypes.IndexFileBaseType
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter

import scala.collection.JavaConversions._
import java.net.{URL, URI}
import java.io.File
import scala.util.Try
import scala.xml.Elem
import scala.io.Source

case class ReferenceMetaData(description: String,
                             maxContigLength: Int,
                             nContigs: Int,
                             referenceType: String,
                             totalLength: Int)

case class ReferenceContig(name: String,
                           description: String,
                           length: Int, md5: String)

case class ReferenceIndexFile(indexType: String,
                              path: String)

case class ReferenceEntryRecord(id: String,
                                metadata: ReferenceMetaData,
                                organism: String,
                                ploidy: String,
                                contigs: Seq[ReferenceContig])

case class ReferenceEntryIO(record: ReferenceEntryRecord,
                            fastaFile: String,
                            indexFiles: Seq[ReferenceIndexFile])

object ReferenceEntry extends PacBioFileReader[ReferenceEntryIO] {

  def loadFromElement(root: Elem): ReferenceEntryIO = {
    // This is often the name
    val rid = (root \ "@id").text

    def orDefault(default: String, aString: String): String = {
      aString.isEmpty match {
        case true => default
        case false => aString
      }
    }
    val orUnknown = orDefault("unknown", _: String)

    val name = orUnknown((root \ "organism" \ "name").text)
    val ply = orUnknown((root \ "organism" \ "ploidy").text)

    val fastaFile = (root \ "reference" \ "file").text

    val referenceIndexFiles = (root \ "reference" \ "index_file").map { i =>
      val indexType = (i \ "@type").text
      ReferenceIndexFile(indexType, i.text)
    }

    val contigs = (root \ "contigs" \ "contig").map { i =>
      val name = (i \ "@id").text
      val description = (i \ "@displayName").text
      val length = (i \ "@length").text.toInt
      val digest = (i \ "digest").text
      ReferenceContig(name, description, length, digest)
    }

    val totalLength = contigs.map(i => i.length).sum

    val metadatas = (root \ "reference").map { i =>
      val description = i \ "description"
      val m = i \ "max_contig_length"
      val n = i \ "num_contigs"
      val t = i \ "type"
      ReferenceMetaData(description.text, m.text.toInt, n.text.toInt, t.text, totalLength)
    }

    val record = ReferenceEntryRecord(rid, metadatas.head, rid, ply, contigs)
    ReferenceEntryIO(record, fastaFile, referenceIndexFiles)
  }

  /**
   * Try to find the *.fasta.contig.index file in the sequence/ directory
   *
   * MK. For reasons that unclear to me, the Reference.Info.XML doesn't
   * contain an entry for the FastaContigIndex file, but the file often
   * exists in the /sequence directory.
   *
   * Adding a hack to look for the file and explicitly add the index file to the
   * ReferenceSet (if it exists)
   *
   * Eventually, this should be a required file as part of the spec.
   *
   * @param rootDir Root ReferenceInfo Directory
   * @return
   */
  private def getIndexFromDir(ext: String, metaTypeId: String, rootDir: Path): Option[ReferenceIndexFile] = {
    Try {
      rootDir.resolve("sequence")
        .toFile.listFiles.find(x => x.getName.endsWith(ext))
        .map(i => ReferenceIndexFile(metaTypeId, i.toPath.toAbsolutePath.toString))
    } getOrElse None
  }

  /**
   * Load the file from the sequence/{fileType_id} or from index_file element parsed from the XML
   *
   * @param indexType RS era inded metatype
   * @param rio ReferenceEntryIO
   * @param rootDir root directory
   * @return
   */
  def getIndexFrom(indexType: IndexFileBaseType, rio: ReferenceEntryIO, rootDir: Path): Option[ReferenceIndexFile] = {
    rio.indexFiles.find(x => x.indexType == indexType.fileTypeId) match {
      case Some(x) => Some(x)
      case _ => getIndexFromDir(indexType.fileExt, indexType.fileTypeId, rootDir)
    }
  }

  def loadFrom(file: File): ReferenceEntryIO = {
    val root = scala.xml.XML.loadFile(file)
    val rootDir = file.toPath.getParent
    val rio = loadFromElement(root)

    val fx = for {
      f1 <- getIndexFrom(FileTypes.RS_I_FCI, rio, rootDir)
      f2 <- getIndexFrom(FileTypes.RS_I_SAM_INDEX, rio, rootDir)
      f3 <- getIndexFrom(FileTypes.RS_I_INDEXER, rio, rootDir)
      f4 <- getIndexFrom(FileTypes.RS_I_SAW, rio, rootDir)
    } yield ReferenceEntryIO(rio.record, rio.fastaFile, Seq(f1, f2, f3, f4))

    // Just fall back to the Loaded file.
    // FIXME This should raise an exception if it can't be loaded
    fx getOrElse rio

  }
}