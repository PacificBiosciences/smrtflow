import java.nio.file.{Paths, Files}

import com.pacbio.secondary.analysis.converters.FastaConverter._
import com.pacbio.secondary.analysis.converters.InValidFastaFileError
import com.pacbio.secondary.analysis.legacy.ReferenceContig
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

/**
 * Tests for validating PacBio spec'ed Fasta file
 * Created by mkocher on 8/25/15.
 */
class ConvertUtilsFastaValidateSpec extends Specification with LazyLogging{

  sequential

  val ROOT_DIR = "pacbio-fasta-spec-files"

  def convertFasta(name: String): Either[InValidFastaFileError, Seq[ReferenceContig]] = {
    val uri = getClass.getResource(s"$ROOT_DIR/$name")
    logger.debug(s"validating fasta file ${uri.toString}")
    val path = Paths.get(uri.toURI)
    validateFastaFile(path)
  }

  "Validate a Fasta file" should {
    "Simple fasta file" in {
      val name = "good-simple_01.fasta"
      val result = convertFasta(name)
      logger.debug(s"error ${result.left}")
      result.isRight must beTrue
    }
    "Bad Example with Double Quote in header" in {
      val name = "bad-double_quote.fasta"
      val result = convertFasta(name)
      logger.debug(s"Expected error ${result.left}")
      result.isLeft must beTrue
    }
    "Bad Example with colon in header" in {
      val name = "bad-colon_in_header.fasta"
      val result = convertFasta(name)
      result.isLeft must beTrue
    }
  }
}
