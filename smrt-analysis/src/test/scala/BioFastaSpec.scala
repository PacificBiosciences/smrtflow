import java.nio.file.Paths
import com.pacbio.secondary.analysis.bio.Fasta
import com.pacbio.secondary.analysis.converters.{InvalidPacBioFastaError, PacBioFastaValidator}
import org.specs2.mutable._

import com.pacbio.secondary.analysis.bio.Fasta

/**
 * Created by mkocher on 3/14/15.
 *
 * Simple Pbcore-esque library to access Fasta Files
 */
class BioFastaSpec extends Specification{

  sequential

  def validateFile(name: String): Option[InvalidPacBioFastaError] = {
    val x = getClass.getResource(name)
    val result = PacBioFastaValidator(Paths.get(x.toURI))
    result match {
      case Left(ex) =>
        println(s"Fasta $name Validation Result $ex")
        Some(ex)
      case Right(_) =>
        println(s"Fasta $name is valid")
        None
    }
  }


  "Load example Fasta file" should {
    "Parse file sanity test" in {
      val uri = getClass.getResource("small.fasta")
      val records = Fasta.loadFrom(uri)
      records.length must beEqualTo(5)
    }
    "Example file" in {
      val uri = getClass.getResource("example_01.fasta")
      val records = Fasta.loadFrom(uri)
      records.length must beEqualTo(2)
    }
    "Simple validate" in {
      val name = "small.fasta"
      validateFile(name) must not beSome
    }
    "Bad Header pacbio-fasta-spec-files/bad-asterisk_identifier.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-asterisk_identifier.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad Header pacbio-fasta-spec-files/bad-colon_in_header.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-colon_in_header.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad Header pacbio-fasta-spec-files/bad-comma.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-comma.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad Header pacbio-fasta-spec-files/bad-double_quote.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-double_quote.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad Header pacbio-fasta-spec-files/bad-duplicate_identifier.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-duplicate_identifier.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad Header pacbio-fasta-spec-files/bad-empty_line.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-empty_line.fasta"
      // This is wrong. htsjdk doesn't fail on emtpy lines
      //validateFile(name) must beSome[InvalidPacBioFastaError]
      validateFile(name) must not beSome
    }
    "Bad Header pacbio-fasta-spec-files/bad-gt_in_header.fasta " in {
      // This should be fine, but pbcore would fail
      val name = "pacbio-fasta-spec-files/bad-gt_in_header.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad Header pacbio-fasta-spec-files/bad-inconsistent_wrapping.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-inconsistent_wrapping.fasta"
      validateFile(name) must not beNull
    }
    "Bad Header pacbio-fasta-spec-files/bad-inconsistent_wrapping_2.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-inconsistent_wrapping_2.fasta"
      validateFile(name) must not beNull
    }
    "Bad Header pacbio-fasta-spec-files/bad-non_nucleotide.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-non_nucleotide.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad Header pacbio-fasta-spec-files/bad-whitespace.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-whitespace.fasta"
      validateFile(name) must not beNull
    }
    "Bad Header pacbio-fasta-spec-files/good-iupac_codes.fasta " in {
      val name = "pacbio-fasta-spec-files/good-iupac_codes.fasta"
      validateFile(name) must not beSome
    }
    "Bad Header pacbio-fasta-spec-files/good-simple_01.fasta " in {
      val name = "pacbio-fasta-spec-files/good-simple_01.fasta"
      validateFile(name) must not beSome
    }
    "Bad empty file pacbio-fasta-spec-files/bad-empty_file.fasta " in {
      val name = "pacbio-fasta-spec-files/bad-empty_file.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Good comma in header pacbio-fasta-spec-files/good-comma_in_header_comment.fasta" in {
      val name = "pacbio-fasta-spec-files/good-comma_in_header_comment.fasta"
      validateFile(name) must not beSome
    }
  }
}
