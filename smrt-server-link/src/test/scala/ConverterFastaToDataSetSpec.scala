import java.nio.file.{Paths, Files}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.bio.Fasta
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.converters.FastaToReferenceConverter
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{NullJobResultsWriter,AnalysisJobStates}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ConvertImportFastaOptions


/**
 * Tests for converting a Fasta to reference DataSet. This has dependencies on both sawriter and samtools.
 *
 * The tests will be skipped if the exe's are not in the path
 *
 * Created by mkocher on 5/1/15.
 */
class ConverterFastaToDataSetSpec extends Specification with LazyLogging {

  // This is for testing
  val HAVE_NGMLR = CallNgmlrIndex.isAvailable()
  val HAVE_SAWRITER = CallSaWriterIndex.isAvailable()
  args(skipAll = !(CallSaWriterIndex.isAvailable() && CallNgmlrIndex.isAvailable()))

  private def runFastaToReference(referenceName: String) = {
    val name = "example_01.fasta"
    val path = getClass.getResource(name)
    val f = Fasta.loadFrom(path)
    f must not beNull
    val outputDir = Files.createTempDirectory("reference-dataset")
    // Copy fasta file to temp dir
    val tmpFasta = outputDir.resolve("example.fasta")
    Files.copy(Paths.get(path.toURI), tmpFasta)
    logger.info(s"Writing Reference Dataset to $outputDir")
    val referenceName = "Dragon"
    val ploidy = Option("Haploid")
    val organism = Option("Lambda")
    FastaToReferenceConverter(referenceName, organism, ploidy,
                              tmpFasta, outputDir).right.get
  }

  "Convert Fasta to Reference Dataset XML" should {
    "Sanity test" in {
      val x = runFastaToReference("Dragon")
      x.path.toFile.exists must beTrue
    }
    "Hyphen in name" in {
      val x = runFastaToReference("aaa-bbb_ccc_123456")
      x.path.toFile.exists must beTrue
    }
  }

  private def runConvertJob(referenceName: String) = {
    val name = "example_01.fasta"
    val writer = new NullJobResultsWriter
    val path = Paths.get(getClass.getResource(name).toURI)
    val opts = ConvertImportFastaOptions(path.toString, referenceName, "Haploid", "Lambda")
    val outputDir = Files.createTempDirectory("fasta-job-test")
    val job = JobResource(UUID.randomUUID, outputDir)
    println(s"Merge job output dir is ${outputDir.toString}")
    val j = opts.toJob
    val startedAt = JodaDateTime.now()
    j.run(job, writer)
  }

  "Use Job Interface" should {
    "Sanity test" in {
      val jobResult = runConvertJob("Dragon")
      jobResult.isRight must beTrue
      val datastore = jobResult.right.get.asInstanceOf[PacBioDataStore]
      val rs = datastore.files.filter(_.fileTypeId == FileTypes.DS_REFERENCE.fileTypeId).head
      Paths.get(rs.path).toFile.exists must beTrue
    }
    "Hyphen in name" in {
      val jobResult = runConvertJob("aaa-bbb_ccc_123456")
      jobResult.isRight must beTrue
      val datastore = jobResult.right.get.asInstanceOf[PacBioDataStore]
      val rs = datastore.files.filter(_.fileTypeId == FileTypes.DS_REFERENCE.fileTypeId).head
      Paths.get(rs.path).toFile.exists must beTrue
    }
  }
}
