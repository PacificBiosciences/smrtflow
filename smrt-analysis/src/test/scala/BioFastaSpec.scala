import java.nio.file.{Paths, Files}
import com.pacbio.secondary.analysis.bio.Fasta
import com.pacbio.secondary.analysis.converters.{InvalidPacBioFastaError, PacBioFastaValidator, FastaIndexWriter}
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
      val faidx = new FastaIndexWriter{}.createFaidx(Paths.get(uri.getPath()))
      Files.exists(Paths.get(faidx)) must beTrue
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
    "Bad: asterisk identifier" in {
      val name = "pacbio-fasta-spec-files/bad-asterisk_identifier.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: colon in header" in {
      val name = "pacbio-fasta-spec-files/bad-colon_in_header.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: comma in header" in {
      val name = "pacbio-fasta-spec-files/bad-comma.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: double-quote in header" in {
      val name = "pacbio-fasta-spec-files/bad-double_quote.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: duplicate identifier" in {
      val name = "pacbio-fasta-spec-files/bad-duplicate_identifier.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: empty line" in {
      val name = "pacbio-fasta-spec-files/bad-empty_line.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: empty line (DOS format)" in {
      val name = "pacbio-fasta-spec-files/bad-empty_line_dos.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: last line empty" in {
      val name = "pacbio-fasta-spec-files/bad-empty_last_line.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: last line empty (DOS format)" in {
      val name = "pacbio-fasta-spec-files/bad-empty_last_line_dos.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: gt symbol in header" in {
      // This should be fine, but pbcore would fail
      val name = "pacbio-fasta-spec-files/bad-gt_in_header.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: inconsistent wrapping" in {
      val name = "pacbio-fasta-spec-files/bad-inconsistent_wrapping.fasta"
      validateFile(name) must not beNull
    }
    "Bad: inconsistent wrapping (example 2)" in {
      val name = "pacbio-fasta-spec-files/bad-inconsistent_wrapping_2.fasta"
      validateFile(name) must not beNull
    }
    "Bad: non-nucleotide characters" in {
      val name = "pacbio-fasta-spec-files/bad-non_nucleotide.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: extra whitespace" in {
      val name = "pacbio-fasta-spec-files/bad-whitespace.fasta"
      validateFile(name) must not beNull
    }
    "Bad: mixed DOS and Unix line endings" in {
      val name = "pacbio-fasta-spec-files/bad_mixed_dos_unix.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Bad: empty file" in {
      val name = "pacbio-fasta-spec-files/bad-empty_file.fasta"
      validateFile(name) must beSome[InvalidPacBioFastaError]
    }
    "Good: simple example" in {
      val name = "pacbio-fasta-spec-files/good-simple_01.fasta"
      validateFile(name) must not beSome
    }
    "Good: IUPAC base codes" in {
      val name = "pacbio-fasta-spec-files/good-iupac_codes.fasta"
      validateFile(name) must not beSome
    }
    "Good: comma in header" in {
      val name = "pacbio-fasta-spec-files/good-comma_in_header_comment.fasta"
      validateFile(name) must not beSome
    }
    "Good: DOS line breaks" in {
      val name = "pacbio-fasta-spec-files/good_dos_format.fasta"
      validateFile(name) must not beSome
    }
  }
}
