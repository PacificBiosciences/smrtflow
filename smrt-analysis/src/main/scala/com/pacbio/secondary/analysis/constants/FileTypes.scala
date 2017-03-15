package com.pacbio.secondary.analysis.constants

/**
 * This is essentially a port of pbsmrtpipe.models.py
 *
 * Enum of all supported FileTypes
 *
 * Created by mkocher on 5/27/15.
 *
 * List from AK
 *
 * PacBio.DataSet.AlignmentSet
 * PacBio.DataSet.BarcodeSet
 * PacBio.DataSet.CCSreadSet
 * PacBio.DataSet.ContigSet
 * PacBio.DataSet.ReferenceSet
 * PacBio.DataSet.SubreadSet
 * PacBio.AlignmentFile.AlignmentBamFile
 * PacBio.BarcodeFile.BarcodeFastaFile
 * PacBio.CCSreadFile.CCSreadBamFile
 * PacBio.Index.SamIndex
 * PacBio.Index.SaWriterIndex
 * PacBio.ReferenceFile.ReferenceFastaFile
 * PacBio.SubreadFile.BaxFile
 * PacBio.SubreadFile.SubreadBamFile
 *
 * Not sure how to translate this non-dataset files, but
 * this will work. This provides parity with pbsmrtpipe.
 *
 * PacBio.FileTypes.Fasta
 * PacBio.FileTypes.Fastq
 * PacBio.FileTypes.GFF
 *
 */
object FileTypes {

  def toI(prefix: String, x: String) = s"PacBio.$prefix.$x"

  def toDS(x: String) = toI("DataSet", x)

  def toFT(x: String) = toI("FileTypes", x)

  object MimeTypes {
    BINARY = "application/octet-stream"
    HTML = "text/html"
    XML = "application/xml"
    TXT = "text/plain"
    JSON = "application/json"
    GZIP = "application/x-gzip"
    ZIP = "application/zip"
  }

  trait FileType {
    val fileTypeId: String
    val baseFileName: String
    val fileExt: String
    val mimeType: String
  }
  // Index File types
  trait PacBioIndexType

  // DataSet Type
  trait PacBioDataSetType

  case class FileBaseType(fileTypeId: String, baseFileName: String, fileExt: String, mimeType: String) extends FileType
  case class IndexFileBaseType(fileTypeId: String, baseFileName: String, fileExt: String, mimeType: String) extends FileType with PacBioIndexType
  case class DataSetBaseType(fileTypeId: String, baseFileName: String, fileExt: String, mimeType: String) extends FileType with PacBioDataSetType {
    def dsName: String = fileTypeId.split(".").last
  }

  // The most generic file type
  final val TXT = FileBaseType(toFT("txt"), "file", "txt", MimeTypes.TXT)

  final val LOG = FileBaseType(toFT("log"), "file", "log", MimeTypes.TXT)

  final val FASTA = FileBaseType(toFT("Fasta"), "file", "fasta", MimeTypes.TXT)
  final val FASTQ = FileBaseType(toFT("Fastq"), "file", "fastq", MimeTypes.TXT)
  final val GFF = FileBaseType(toFT("gff"), "file", "gff", MimeTypes.TXT)
  final val VCF = FileBaseType(toFT("vcf"), "file", "vcf", MimeTypes.TXT)
  final val CSV = FileBaseType(toFT("csv"), "file", "csv", MimeTypes.TXT)
  final val XML = FileBaseType(toFT("xml"), "file", "xml", MimeTypes.XML)
  final val HTML = FileBaseType(toFT("html"), "file", "html", MimeTypes.HTML)
  final val JSON = FileBaseType(toFT("json"), "file", "json", MimeTypes.JSON)
  final val BIGWIG = FileBaseType(toFT("bigwig"), "file", "bw", MimeTypes.BINARY)
  final val GZIP = FileBaseType(toFT("gzip"), "file", "gz", MimeTypes.GZIP)
  final val ZIP = FileBaseType(toFT("zip"), "file", "zip", MimeTypes.ZIP)

  final val REPORT = FileBaseType(toFT("JsonReport"), "file", "report.json", MimeTypes.JSON)

  // FIXME This is duplicated in DataSetMetaTypes
  final val DS_SUBREADS = DataSetBaseType(toDS("SubreadSet"), "file", "subreadset.xml", MimeTypes.XML)
  final val DS_HDF_SUBREADS = DataSetBaseType(toDS("HdfSubreadSet"), "file", "hdfsubreadset.xml", MimeTypes.XML)
  final val DS_ALIGNMENTS = DataSetBaseType(toDS("AlignmentSet"), "file", "alignmentset.xml", MimeTypes.XML)
  final val DS_CCS = DataSetBaseType(toDS("ConsensusReadSet"), "file", "consensusreadset.xml", MimeTypes.XML)
  final val DS_REFERENCE = DataSetBaseType(toDS("ReferenceSet"), "file", "referenceset.xml", MimeTypes.XML)
  final val DS_BARCODE = DataSetBaseType(toDS("BarcodeSet"), "file", "barcodeset.xml", MimeTypes.XML)
  final val DS_CONTIG = DataSetBaseType(toDS("ContigSet"), "file", "contigset.xml", MimeTypes.XML)
  final val DS_CCS_ALIGNMENTS = DataSetBaseType(toDS("ConsensusAlignmentSet"), "file", "consensusalignmentset.xml", MimeTypes.XML)
  final val DS_GMAP_REF = DataSetBaseType(toDS("GmapReferenceSet"), "file", "gmapreferenceset.xml", MimeTypes.XML)


  // 'File' types
  //# PacBio Defined Formats
  final val FASTA_BC = FileBaseType("PacBio.BarcodeFile.BarcodeFastaFile", "file", "barcode.fasta", MimeTypes.TXT)
  // No ':' or '"' in the id
  final val FASTA_REF = FileBaseType("PacBio.ReferenceFile.ReferenceFastaFile", "file", "pbreference.fasta", MimeTypes.TXT)

  // Used in ContigSet
  final val FASTA_CONTIG = FileBaseType("PacBio.ContigFile.ContigFastaFile", "file", "contig.fasta", MimeTypes.TXT)

  final val BAM_ALN = FileBaseType("PacBio.AlignmentFile.AlignmentBamFile", "file", "alignment.bam", MimeTypes.BINARY)
  final val BAM_SUB = FileBaseType("PacBio.SubreadFile.SubreadBamFile", "file", "subread.bam", MimeTypes.BINARY)
  final val BAM_CCS = FileBaseType("PacBio.ConsensusReadFile.ConsensusReadBamFile", "file", "ccs.bam", MimeTypes.BINARY)
  final val BAM_CCS_ALN = FileBaseType("PacBio.AlignmentFile.ConsensusAlignmentBamFile", "file", "ccs_align.bam", MimeTypes.BINARY)

  // FIXME. Add Bax/Bam Formats here. This should replace the exiting pre-SA3 formats.
  final val BAX = FileBaseType("PacBio.SubreadFile.BaxFile", "file", "bax.h5", MimeTypes.BINARY)

  // Internal File Format
  final val BAZ = FileBaseType("PacBio.ReadFile.BazFile", "file", "baz", MimeTypes.BINARY)
  final val TRC = FileBaseType("PacBio.ReadFile.TraceFile", "file", "trc", MimeTypes.BINARY)
  final val PLS = FileBaseType("PacBio.ReadFile.PulseFile", "file", "pls", MimeTypes.BINARY)
  // THIS IS EXPERIMENT for internal analysis. DO NOT use
  final val COND = FileBaseType("PacBio.FileTypes.COND", "file", "conditions.json", MimeTypes.JSON)

  // Index File Types
  final val I_SAW = IndexFileBaseType("PacBio.Index.SaWriterIndex", "file", "fasta.sa", MimeTypes.BINARY)
  final val I_SAM = IndexFileBaseType("PacBio.Index.SamIndex", "file", "fai", MimeTypes.BINARY)

  // Pacbio BAM pbi file
  final val I_PBI = IndexFileBaseType("PacBio.Index.PacBioIndex", "file", "pbi", MimeTypes.BINARY)
  final val I_BAI = IndexFileBaseType("PacBio.Index.BamIndex", "file", "bam.bai", MimeTypes.BINARY)

  // NGMLR indices
  final val I_NGMLR = IndexFileBaseType("NgmlrIndex"), "file", ".ngm", MimeTypes.BINARY)

  // Files used by SMRT View
  // "Indexer" (name in reference.info.xml) file used by SMRT View
  final val I_INDEX = IndexFileBaseType("PacBio.Index.Indexer", "file", "fasta.index", MimeTypes.TXT)
  // fasta.contig.index index file used by SMRT View
  final val I_FCI = IndexFileBaseType("PacBio.Index.FastaContigIndex", "file", "fasta.contig.index", MimeTypes.TXT)

  // Only Supported RS era Reference Index file types, which are used to
  // convert to ReferenceSet
  final val RS_I_SAM_INDEX = IndexFileBaseType("sam_idx", "file", "fasta.fai", MimeTypes.TXT)
  final val RS_I_INDEXER = IndexFileBaseType("indexer", "file", "fasta.index", MimeTypes.TXT)
  final val RS_I_FCI = IndexFileBaseType("fasta_contig_index", "file", "fasta.contig.index", MimeTypes.TXT)
  final val RS_I_SAW = IndexFileBaseType("sawriter", "file", "fasta.sa", MimeTypes.TXT)

  // Various other file types specific to SubreadSet
  def toSF(x: String) = toI("SubreadFile", x)
  final val STS_XML = FileBaseType(toSF("ChipStatsFile"), "file", "sts.xml", MimeTypes.XML)
  final val STS_H5 = FileBaseType(toSF("ChipStatsH5File"), "file", "sts.h5", MimeTypes.BINARY)
  final val BAM_SCRAPS = FileBaseType(toSF("ScrapsBamFile"), "file", ".scraps.bam", MimeTypes.BINARY)
  final val BAM_HQ_REGION = FileBaseType(toSF("HqRegionBamFile"), "file", ".bam", MimeTypes.BINARY)
  final val BAM_SCRAPS_HQ = FileBaseType(toSF("HqScrapsBamFile"), "file", ".scraps.bam", MimeTypes.BINARY)
  final val BAM_LQ_REGION = FileBaseType(toSF("LqRegionBamFile"), "file", ".bam", MimeTypes.BINARY)
  final val BAM_SCRAPS_LQ = FileBaseType(toSF("LqScrapsBamFile"), "file", ".scraps.bam", MimeTypes.BINARY)
  final val BAM_ZMW = FileBaseType(toSF("PolymeraseBamFile"), "file", ".bam", MimeTypes.BINARY)
  final val BAM_P_SCRAPS = FileBaseType(toSF("PolymeraseScrapsBamFile"), "file", ".scraps.bam", MimeTypes.BINARY)
  final val FASTA_ADAPTER = FileBaseType(toSF("AdapterFastaFile"), "adapter", ".fasta", MimeTypes.TXT)
  final val FASTA_CONTROL = FileBaseType(toSF("ControlFastaFile"), "control", ".fasta", MimeTypes.TXT)

  final val BAM_RESOURCES = Seq(BAM_ALN, BAM_SUB, BAM_CCS, BAM_ALN, I_PBI, I_BAI, STS_XML, STS_H5, BAM_SCRAPS, BAM_HQ_REGION, BAM_SCRAPS_HQ, BAM_LQ_REGION, BAM_SCRAPS_LQ, BAM_ZMW, BAM_P_SCRAPS, FASTA_ADAPTER, FASTA_CONTROL).toSet
}
