import java.nio.file.{Paths, Files}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.converters.FastaToReferenceConverter
import com.pacbio.secondary.smrtlink.analysis.externaltools._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._

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
  args(
    skipAll =
      !(CallSaWriterIndex.isAvailable() && CallNgmlrIndex.isAvailable()))

  private def runFastaToReference(referenceName: String) = {
    val name = "example_01.fasta"
    val uri = getClass.getResource(name).toURI
    val path = Paths.get(uri)

    Files.exists(path) must beTrue

    val outputDir = Files.createTempDirectory("reference-dataset")
    // Copy fasta file to temp dir
    val tmpFasta = outputDir.resolve("example.fasta")
    Files.copy(path, tmpFasta)
    logger.info(s"Writing Reference Dataset to $outputDir")
    val referenceName = "Dragon"
    val ploidy = Option("Haploid")
    val organism = Option("Lambda")
    FastaToReferenceConverter(referenceName,
                              organism,
                              ploidy,
                              tmpFasta,
                              outputDir).right.get
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
}
