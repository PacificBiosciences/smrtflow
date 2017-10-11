import java.nio.file.{Files, Paths, Path}
import java.util.UUID
import org.apache.commons.io.FileUtils

import org.specs2.mutable._
import org.specs2.specification.BeforeAfterEach

import com.pacbio.secondary.smrtlink.analysis.converters.FastaBarcodesConverter

class FastaToBarcodesSpec extends Specification with BeforeAfterEach {
  lazy val tmpDir: Path = Files.createTempDirectory("test-barcodes")

  def before = true // placeholder
  def after = FileUtils.deleteDirectory(tmpDir.toFile)

  def generateBarcodes(fastaPath: Path): Boolean = {
    val name = UUID.randomUUID().toString
    FastaBarcodesConverter(name, fastaPath, tmpDir, mkdir = true) match {
      case Right(io) => true
      case Left(err) => false
    }
  }

  "Generate BarcodeSet" should {
    "Import valid FASTA file" in {
      val uri = getClass.getResource("barcode-fasta/barcodes.fasta")
      val fastaPath = Paths.get(uri.getPath())
      generateBarcodes(fastaPath) must beTrue
    }
    "Import FASTA with variable sequence lengths" in {
      val uri = getClass.getResource("barcode-fasta/variable_lengths.fasta")
      val fastaPath = Paths.get(uri.getPath())
      generateBarcodes(fastaPath) must beFalse
    }
  }
}
