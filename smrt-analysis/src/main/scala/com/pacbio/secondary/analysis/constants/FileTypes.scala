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
  final val TXT = FileBaseType(toFT("txt"), "file", "txt", "text/plain")

  final val LOG = FileBaseType(toFT("log"), "file", "log", "text/plain")

  final val FASTA = FileBaseType(toFT("Fasta"), "file", "fasta", "text/plain")
  final val FASTQ = FileBaseType(toFT("Fastq"), "file", "fastq", "text/plain")
  final val GFF = FileBaseType(toFT("Gff"), "file", "gff", "text/plain")
  final val VCF = FileBaseType(toFT("Vcf"), "file", "vcf", "text/plain")
  final val CSV = FileBaseType(toFT("Csv"), "file", "csv", "text/plain")
  final val XML = FileBaseType(toFT("Xml"), "file", "xml", "application/xml")
  final val JSON = FileBaseType(toFT("Json"), "file", "json", "application/json")
  final val BIGWIG = FileBaseType(toFT("BigWig"), "file", "bw", "application/octet-stream")
  final val GZIP = FileBaseType(toFT("gzip"), "file", "gz", "application/x-gzip")
  final val ZIP = FileBaseType(toFT("zip"), "file", "zip", "application/zip")

  final val REPORT = FileBaseType(toFT("JsonReport"), "file", "report.json", "application/json")

  // FIXME This is duplicated in DataSetMetaTypes
  final val DS_SUBREADS = DataSetBaseType(toDS("SubreadSet"), "file", "subreadset.xml", "application/xml")
  final val DS_HDF_SUBREADS = DataSetBaseType(toDS("HdfSubreadSet"), "file", "hdfsubreadset.xml", "application/xml")
  final val DS_ALIGNMENTS = DataSetBaseType(toDS("AlignmentSet"), "file", "alignmentset.xml", "application/xml")
  final val DS_CCS = DataSetBaseType(toDS("ConsensusReadSet"), "file", "consensusreadset.xml", "application/xml")
  final val DS_REFERENCE = DataSetBaseType(toDS("ReferenceSet"), "file", "referenceset.xml", "application/xml")
  final val DS_BARCODE = DataSetBaseType(toDS("BarcodeSet"), "file", "barcodeset.xml", "application/xml")
  final val DS_CONTIG = DataSetBaseType(toDS("ContigSet"), "file", "contigset.xml", "application/xml")
  final val DS_CCS_ALIGNMENTS = DataSetBaseType(toDS("ConsensusAlignmentSet"), "file", "consensusalignmentset.xml", "application/xml")
  final val DS_GMAP_REF = DataSetBaseType(toDS("GmapReferenceSet"), "file", "gmapreferenceset.xml", "application/xml")


  // 'File' types
  //# PacBio Defined Formats
  final val FASTA_BC = FileBaseType("PacBio.BarcodeFile.BarcodeFastaFile", "file", "barcode.fasta", "text/plain")
  // No ':' or '"' in the id
  final val FASTA_REF = FileBaseType("PacBio.ReferenceFile.ReferenceFastaFile", "file", "pbreference.fasta", "text/plain")

  // Used in ContigSet
  final val FASTA_CONTIG = FileBaseType("PacBio.ContigFile.ContigFastaFile", "file", "contig.fasta", "text/plain")

  final val BAM_ALN = FileBaseType("PacBio.AlignmentFile.AlignmentBamFile", "file", "alignment.bam", "application/octet-stream")
  final val BAM_SUB = FileBaseType("PacBio.SubreadFile.SubreadBamFile", "file", "subread.bam", "application/octet-stream")
  final val BAM_CCS = FileBaseType("PacBio.ConsensusReadFile.ConsensusReadBamFile", "file", "ccs.bam", "application/octet-stream")
  final val BAM_CCS_ALN = FileBaseType("PacBio.AlignmentFile.ConsensusAlignmentBamFile", "file", "ccs_align.bam", "application/octet-stream")

  // FIXME. Add Bax/Bam Formats here. This should replace the exiting pre-SA3 formats.
  final val BAX = FileBaseType("PacBio.SubreadFile.BaxFile", "file", "bax.h5", "application/octet-stream")

  // Internal File Format
  final val BAZ = FileBaseType("PacBio.ReadFile.BazFile", "file", "baz", "application/octet-stream")
  final val TRC = FileBaseType("PacBio.ReadFile.TraceFile", "file", "trc", "application/octet-stream")
  final val PLS = FileBaseType("PacBio.ReadFile.PulseFile", "file", "pls", "application/octet-stream")
  // THIS IS EXPERIMENT for internal analysis. DO NOT use
  final val COND = FileBaseType("PacBio.FileTypes.COND", "file", "conditions.json", "application/json")

  // Index File Types
  final val I_SAW = IndexFileBaseType("PacBio.Index.SaWriterIndex", "file", "fasta.sa", "application/octet-stream")
  final val I_SAM = IndexFileBaseType("PacBio.Index.SamIndex", "file", "fai", "application/octet-stream")

  // Pacbio BAM pbi file
  final val I_PBI = IndexFileBaseType("PacBio.Index.PacBioIndex", "file", "pbi", "application/octet-stream")
  final val I_BAI = IndexFileBaseType("PacBio.Index.BamIndex", "file", "bam.bai","application/octet-stream")

  // Files used by SMRT View
  // "Indexer" (name in reference.info.xml) file used by SMRT View
  final val I_INDEX = IndexFileBaseType("PacBio.Index.Indexer", "file", "fasta.index", "text/plain")
  // fasta.contig.index index file used by SMRT View
  final val I_FCI = IndexFileBaseType("PacBio.Index.FastaContigIndex", "file", "fasta.contig.index", "text/plain")

  // Only Supported RS era Reference Index file types, which are used to
  // convert to ReferenceSet
  final val RS_I_SAM_INDEX = IndexFileBaseType("sam_idx", "file", "fasta.fai", "text/plain")
  final val RS_I_INDEXER = IndexFileBaseType("indexer", "file", "fasta.index", "text/plain")
  final val RS_I_FCI = IndexFileBaseType("fasta_contig_index", "file", "fasta.contig.index", "text/plain")
  final val RS_I_SAW = IndexFileBaseType("sawriter", "file", "fasta.sa", "text/plain")

  // Various other file types specific to SubreadSet
  def toSF(x: String) = toI("SubreadFile", x)
  final val STS_XML = FileBaseType(toSF("ChipStatsFile"), "file", "sts.xml", "application/xml")
  final val STS_H5 = FileBaseType(toSF("ChipStatsH5File"), "file", "sts.h5", "application/octet-stream")
  final val BAM_SCRAPS = FileBaseType(toSF("ScrapsBamFile"), "file", ".scraps.bam", "application/octet-stream")
  final val BAM_HQ_REGION = FileBaseType(toSF("HqRegionBamFile"), "file", ".bam", "application/octet-stream")
  final val BAM_SCRAPS_HQ = FileBaseType(toSF("HqScrapsBamFile"), "file", ".scraps.bam", "application/octet-stream")
  final val BAM_LQ_REGION = FileBaseType(toSF("LqRegionBamFile"), "file", ".bam", "application/octet-stream")
  final val BAM_SCRAPS_LQ = FileBaseType(toSF("LqScrapsBamFile"), "file", ".scraps.bam", "application/octet-stream")
  final val BAM_ZMW = FileBaseType(toSF("PolymeraseBamFile"), "file", ".bam", "application/octet-stream")
  final val BAM_P_SCRAPS = FileBaseType(toSF("PolymeraseScrapsBamFile"), "file", ".scraps.bam", "application/octet-stream")
  final val FASTA_ADAPTER = FileBaseType(toSF("AdapterFastaFile"), "adapter", ".fasta", "text/plain")
  final val FASTA_CONTROL = FileBaseType(toSF("ControlFastaFile"), "control", ".fasta", "text/plain")

  final val BAM_RESOURCES = Seq(BAM_ALN, BAM_SUB, BAM_CCS, BAM_ALN, I_PBI, I_BAI, STS_XML, STS_H5, BAM_SCRAPS, BAM_HQ_REGION, BAM_SCRAPS_HQ, BAM_LQ_REGION, BAM_SCRAPS_LQ, BAM_ZMW, BAM_P_SCRAPS, FASTA_ADAPTER, FASTA_CONTROL).toSet
}
