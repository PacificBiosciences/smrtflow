package com.pacbio.secondary.smrtlink.analysis.bio

import java.nio.file.Path
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.PacBioFileReader
import com.typesafe.scalalogging.LazyLogging

import scala.io.Source
import java.io.{BufferedWriter, File, FileWriter}

import htsjdk.samtools.reference.{FastaSequenceFile, ReferenceSequence}

import scala.collection.mutable

// Thin wrapper around htsjdk
class FastaIterator(file: File, truncateWhiteSpace: Boolean = true)
    extends Iterator[ReferenceSequence] {
  private val reader = new FastaSequenceFile(file, truncateWhiteSpace)
  private var record = reader.nextSequence()

  def hasNext: Boolean = record != null
  def next: ReferenceSequence = {
    val r = record
    record = reader.nextSequence()
    r
  }
}

/** This uses the pbcore.io data Model
  *
  * @param id       The id of the sequence in the FASTA file, equal to the FASTA header up to the first whitespace.
  * @param header   The comment associated with the sequence in the FASTA file, equal to the contents of the FASTA
  *                 header following the first whitespace
  * @param sequence DNA Sequence
  */
case class FastaRecord(id: String, header: String, sequence: Seq[Char])

trait FastaWriter extends LazyLogging {
  def writeRecords(f: File, records: Seq[FastaRecord]): Unit = {
    val bw = new BufferedWriter(new FileWriter(f))

    for (r <- records) {
      bw.write(s">${r.id} ${r.header}\n")
      // need to pretty format 60 width
      val sx = r.sequence.foldLeft("")((a, c) => s"$a$c")
      bw.write(sx + "\n")
    }
    //logger.info(s"Completed writing ${records.length} fasta records to ${f.getAbsoluteFile}")
    bw.close()
  }

}

object FastaWriter extends FastaWriter
