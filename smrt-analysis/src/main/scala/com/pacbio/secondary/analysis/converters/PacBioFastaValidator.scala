package com.pacbio.secondary.analysis.converters

import java.nio.file.{Files, Path}
import com.typesafe.scalalogging.LazyLogging
import htsjdk.samtools.reference.{FastaSequenceFile, ReferenceSequence}

import scala.collection.immutable.HashSet
import scala.collection.mutable
import scala.io.Source
import scala.util.Try

case class InvalidPacBioFastaError(msg: String) extends Exception(msg)
case class ContigsMetaData(nrecords: Int, totalLength: Long)

object PacBioFastaValidator extends LazyLogging{

  type RefOrE = Either[InvalidPacBioFastaError, ContigsMetaData]
  type OptionE = Option[InvalidPacBioFastaError]
  type RE = ReferenceSequence => OptionE

  val VALID_SEQUENCE_VALUES = "gatcuryswkmbdhvnGATCURYSWKMBDHVN-.".toSet
  val VALID_SEQ_IUPAC_BTYPES = VALID_SEQUENCE_VALUES.map(_.toByte)

  val INVALID_CONTIG_ID_CHARS = HashSet(',', ':', '"')
  // Adding the '>' is for pbcore to not fail; we no longer have this
  // limitation on the Scala (or Java) side
  val INVALID_RAW_HEADER_CHARS = HashSet('>')

  // The raw header can be converted to a "id"
  private def toId(xs: String) = xs.split(" ")(0)


  private def composeValidation(v1: RE, v2: RE): RE = {

    def runner(r: ReferenceSequence): OptionE = {
      v1(r) match {
        case Some(x) => Some(x)
        case _ => v2(r)
      }
    }

    runner _
  }

  def validateDnaByte(x: Byte) = {
    if (VALID_SEQ_IUPAC_BTYPES contains x) None else Some(InvalidPacBioFastaError(s"Invalid Char '${x.toChar}'"))
  }

  def validateDna(r: ReferenceSequence) = {
    // just get the first error
    r.getBases.flatMap(validateDnaByte).headOption
  }

  def startsWithAsterisk(xs: String) = {
    if (xs.startsWith("*")) Some(InvalidPacBioFastaError(s"Contig Id must not start with '*'. $xs")) else None
  }

  def charInHeader(c: Char, xs: String) = {
    if (xs contains c) Some(InvalidPacBioFastaError(s"Invalid '$xs' contains '$c'")) else None
  }

  def validateId(xs: String) = INVALID_CONTIG_ID_CHARS.flatMap(x => charInHeader(x, xs)).headOption

  def validateRawHeader(xs: String) = INVALID_RAW_HEADER_CHARS.flatMap(x => charInHeader(x, xs)).headOption

  def validateRecord(r: ReferenceSequence) = {
    def vheader(r: ReferenceSequence) = validateRawHeader(r.getName)
    def vastrick(r: ReferenceSequence) = startsWithAsterisk(r.getName)
    def vid(r: ReferenceSequence) = validateId(toId(r.getName))
    // FIXME. Clean this up.
    val v1 = composeValidation(vheader, vastrick)
    val v2 = composeValidation(v1, vid)
    val v3 = composeValidation(v2, validateDna)
    v3(r)
  }

  // Simple sanity check to make sure the file is not empty
  def validateRawFasta(path: Path): OptionE = {
    Try {
      val sx = Source.fromFile(path.toFile)
      if (sx.hasNext) {
        var prev: Char = 'X'
        var isUnix = false
        var isDos = false
        while (sx.hasNext && !(isDos && isUnix)) {
          val c: Char = sx.next
          if (c == '\n') {
            if (prev == '\r') isDos = true else isUnix = true
          }
          prev = c
        }
        if (isDos && isUnix) {
          Some(InvalidPacBioFastaError(s"Mixed DOS and Unix line endings"))
        } else None
      } else Some(InvalidPacBioFastaError(s"Emtpy file detected ${path.toAbsolutePath.toString}"))
    } getOrElse Some(InvalidPacBioFastaError(s"Invalid fasta file detected ${path.toAbsolutePath.toString}"))
  }

  def preValidation(path: Path) = {
    if (!Files.exists(path)) Some(InvalidPacBioFastaError(s"Unable to find ${path.toAbsolutePath.toString}")) else validateRawFasta(path)
  }

  /**
   * Core Fasta level Validation for the Fasta File
   *
   * - Header "id" is unique
   * - DNA Sequences are IUPAC +
   * - Header doesn't start with asterisk
   *
   * @param path to Fasta File
   * @return
   */
  def validateFastaFile(path: Path, barcodeMode: Boolean = false): RefOrE = {

    val headerIds = mutable.Set[String]()

    def headerIsUnique(xs: String) = {
      if (headerIds contains xs) Some(InvalidPacBioFastaError(s"Duplicate header id '$xs'")) else None
    }

    def validateUniqueId(r: ReferenceSequence) =  headerIsUnique(toId(r.getName))

    val validatePacBioRecord = composeValidation(validateRecord, validateUniqueId)

    // Use the raw value to validate the contig id
    val truncateNamesAtWhitespace = false
    val f = new FastaSequenceFile(path.toFile, truncateNamesAtWhitespace)

    var error: Option[InvalidPacBioFastaError] = None
    var nrecords: Int = 0
    var totalLength: Long = 0
    var allLengths: Set[Long] = Set[Long]()

    var toBreak = false
    while (!toBreak) {
      val xseq = f.nextSequence()
      xseq match {
        case xs: ReferenceSequence =>
          nrecords += 1
          val seqLen = xseq.length()
          totalLength += seqLen
          allLengths += seqLen
          logger.info(s"Attempting to validate Record $xs")
          validatePacBioRecord(xs) match {
            case Some(ex) =>
              logger.error(s"Failed to validate fasta record $xs. Error ${ex.msg}")
              error = Option(ex)
              toBreak = true
            case _  =>
              val contigId = toId(xs.getName)
              headerIds += contigId
              logger.info(s"successfully validated record $xs")
          }
        // Validate record
        case _ =>
          toBreak = true
      }
    }
    error match {
      case Some(err) => Left(err)
      case None =>
        if ((barcodeMode) && (allLengths.size > 1)) {
          Left(InvalidPacBioFastaError(s"All sequences in barcode FASTA files must be the same length; this file contains sequences of lengths $allLengths"))
        } else Right(ContigsMetaData(nrecords, totalLength))
    }
  }

  /**
   *
   * Validates the file exists as performs detailed Fasta level validation
   *
   *
   * @param path to Fasta file
   * @return
   */
  def apply(path: Path, barcodeMode: Boolean = false): RefOrE = {
    preValidation(path) match {
      case Some(x) => Left(x)
      case _ => validateFastaFile(path, barcodeMode)
    }
  }

  def validate(path: Path, barcodeMode: Boolean = false): ContigsMetaData = {
    apply(path, barcodeMode) match {
      case Left(err) => throw err
      case Right(ctgs) => ctgs
    }
  }
}
