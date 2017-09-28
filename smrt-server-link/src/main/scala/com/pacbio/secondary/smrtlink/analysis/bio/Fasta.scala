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

/**
  * THIS ALL NEEDS TO BE DELETED
  */
trait FastaReader extends PacBioFileReader[Seq[FastaRecord]] {

  def loadFrom(file: File): Seq[FastaRecord] = {

    logger.debug(s"Parsing file $file")
    def lineToId(s: String): String = {
      s.split(" ")(0).tail
    }

    var header = ""
    var seq = ""

    var records = new mutable.MutableList[FastaRecord]()

    val source = Source.fromFile(file)
    val it = source.getLines()
    while (it.hasNext) {
      val s = it.next()
      s match {
        case s: String if s.startsWith(">") && seq != "" =>
          records += FastaRecord(lineToId(header), header, seq)
        case s: String if s.startsWith(">") =>
          // Reinitialize
          seq = ""
          header = s
        case s: String =>
          seq += s
      }
    }
    // Last Record
    if (seq != "") {
      // Empty case
      records += FastaRecord(lineToId(header), header, seq)
    }
    records.toList
  }

}

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

object Fasta extends FastaReader with FastaWriter
