package com.pacbio.secondary.smrtlink.analysis.bio

import java.io.File
import java.nio.file.Path
import java.util.UUID


object FastaMockUtils extends FastaWriter {
  val DNA = Seq('A', 'C', 'G', 'T')

  def mockRecord(i: String, maxSequenceLength: Int = 100): FastaRecord = {

    def randomDna: Char = {
      DNA(scala.util.Random.nextInt(DNA.length))
    }

    def mockDnaSequence = (0 until maxSequenceLength).map(i => randomDna)

    val uuid = UUID.randomUUID()
    FastaRecord(s"record_${uuid.toString}", s"header record ${uuid.toString}", mockDnaSequence)
  }

  def mockRecords(n: Int): Seq[FastaRecord] = {
    (0 until n).map(i => mockRecord(s"record_$i"))
  }

  def parseFile(file: File): Iterator[FastaRecord] = {
    val it = (0 until 25).toIterator
    for (i <- it) yield {
      mockRecord(s"record_$i")
    }
  }

  def writeMockFastaFile(numRecords: Int, p: Path): Path = {
    writeRecords(p.toFile, mockRecords(numRecords))
    p
  }
}
