import java.nio.file.{Paths, Files}

import com.pacbio.secondary.analysis.bio.Fasta
import com.pacbio.secondary.analysis.converters.FastaToReferenceConverter
import com.pacbio.secondary.analysis.externaltools._
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._


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

  "Convert Fasta to Reference Dataset XML" should {
    "Sanity test" in {
      val name = "example_01.fasta"
      val path = getClass.getResource(name)
      val f = Fasta.loadFrom(path)
      val outputDir = Files.createTempDirectory("reference-dataset")
      // Copy fasta file to temp dir
      val tmpFasta = outputDir.resolve("example.fasta")
      Files.copy(Paths.get(path.toURI), tmpFasta)

      logger.info(s"Writing Reference Dataset to $outputDir")
      val referenceName = "Dragon"
      val ploidy = Option("Haploid")
      val organism = Option("Lambda")
      val x = FastaToReferenceConverter(referenceName, organism, ploidy,
                                        tmpFasta, outputDir) match {
        case Right(rio) => rio.path
        case _ => null
      }
      logger.info(s"Reference DataSet File IO $x")
      f must not beNull
    }
  }
}
