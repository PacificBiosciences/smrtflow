import java.nio.file.{Files, Paths, Path}
import java.util.UUID
import org.apache.commons.io.FileUtils

import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll

import com.pacbio.secondary.smrtlink.analysis.converters.FastaBarcodesConverter

class FastaToBarcodesSpec extends Specification with BeforeAfterAll {
  lazy val tmpDir: Path = Files.createTempDirectory("test-barcodes")

  def beforeAll(): Unit = Unit // placeholder
  def afterAll() = FileUtils.deleteDirectory(tmpDir.toFile)

  def generateBarcodes(fastaPath: Path): Boolean = {
    val name = UUID.randomUUID().toString
    FastaBarcodesConverter(name, fastaPath, tmpDir, mkdir = true).isRight
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
      generateBarcodes(fastaPath) must beTrue
    }
  }
}
