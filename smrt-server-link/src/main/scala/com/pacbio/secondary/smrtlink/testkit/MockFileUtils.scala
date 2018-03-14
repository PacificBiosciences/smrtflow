package com.pacbio.secondary.smrtlink.testkit

import java.io.File
import java.nio.file.{Files, Path}
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.bio.{FastaRecord, FastaWriter}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  DataStoreFile,
  EngineJob,
  JobConstants,
  JobTypeIds
}
import org.joda.time.{DateTime => JodaDateTime}

/**
  * Trying to centralize test utils that generating Mock/Test objects, or files.
  *
  */
object MockFileUtils extends FastaWriter {
  val DNA = Seq('A', 'C', 'G', 'T')

  def mockRecord(i: String, maxSequenceLength: Int = 100): FastaRecord = {

    def randomDna: Char = {
      DNA(scala.util.Random.nextInt(DNA.length))
    }

    def mockDnaSequence = (0 until maxSequenceLength).map(i => randomDna)

    val uuid = UUID.randomUUID()
    FastaRecord(s"record_${uuid.toString}",
                s"header record ${uuid.toString}",
                mockDnaSequence)
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

  def writeMockFastaDataStoreFile(numRecords: Int, p: Path): DataStoreFile = {
    val fastaFileUUID = UUID.randomUUID()
    val now = JodaDateTime.now()
    writeMockFastaFile(numRecords, p)

    DataStoreFile(
      fastaFileUUID,
      "mock-fasta",
      FileTypes.FASTA.fileTypeId,
      p.toFile.length(),
      now,
      now,
      p.toAbsolutePath.toString,
      isChunked = false,
      "Mock Fasta",
      ""
    )
  }

  def writeMockTmpFastaFile(numRecords: Int = 10): Path = {
    val p = Files.createTempFile("mock-fasta-file", ".fasta")
    writeMockFastaFile(numRecords, p)
  }

  def toTestRawEngineJob(name: String,
                         jobUUID: Option[UUID],
                         jobType: Option[JobTypeIds.JobType],
                         smrtLinkVersion: Option[String],
                         state: AnalysisJobStates.JobStates =
                           AnalysisJobStates.SUCCESSFUL,
                         projectId: Option[Int] = None): EngineJob = {
    val uuid = jobUUID.getOrElse(UUID.randomUUID())
    val jobTypeId = jobType.getOrElse(JobTypeIds.IMPORT_DATASET)
    val now = JodaDateTime.now()

    EngineJob(
      -1,
      uuid,
      name,
      "comment",
      now,
      now,
      now,
      AnalysisJobStates.CREATED,
      jobTypeId.id,
      "",
      "{}",
      None,
      None,
      smrtLinkVersion,
      projectId = projectId.getOrElse(JobConstants.GENERAL_PROJECT_ID)
    )
  }

  def toTestDataStoreFile(uuid: UUID,
                          fileType: FileTypes.FileType,
                          path: Path,
                          name: String = "mock-file"): DataStoreFile = {
    val now = JodaDateTime.now()
    DataStoreFile(uuid,
                  "source-id",
                  fileType.fileTypeId,
                  0,
                  now,
                  now,
                  path.toAbsolutePath.toString,
                  false,
                  name,
                  "description")
  }

}
