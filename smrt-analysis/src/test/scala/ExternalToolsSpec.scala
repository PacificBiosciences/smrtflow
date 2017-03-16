
import java.nio.file.{Files, Path, Paths}

import org.specs2.mutable.Specification

import com.pacbio.secondary.analysis.externaltools._


class ExternalToolsSpec extends Specification with ExternalToolsUtils{

  sequential

  "Smoke test for external tools" should {
    "find ls in path" in {
      val path = which("ls")
      path must beSome
    }

  }
}

trait FastaSetupUtils {
  this: Specification =>

  def getFasta(name: String): Path = {
    val path = getClass.getResource(name)
    val outputDir = Files.createTempDirectory("externaltools-test")
    // Copy fasta file to temp dir
    val tmpFasta = outputDir.resolve("example.fasta")
    Files.copy(Paths.get(path.toURI), tmpFasta)
    tmpFasta
  }
}

class SaWriterSpec extends Specification with FastaSetupUtils {

  args(skipAll = !CallSaWriterIndex.isAvailable())

  "Test sawriter plugin" should {
    "Sanity test" in {
      val tmpFasta = getFasta("example_01.fasta")
      val result = CallSaWriterIndex.run(tmpFasta)
      val path = result.right.get
      path.toFile.exists must beTrue
    }
  }
}

class NgmlrIndexSpec extends Specification with FastaSetupUtils {

  args(skipAll = !CallNgmlrIndex.isAvailable())

  "Test ngmlr plugin" should {
    "Sanity test" in {
      val tmpFasta = getFasta("example_01.fasta")
      val result = CallNgmlrIndex.run(tmpFasta)
      val paths = result.right.get
      paths.size must beEqualTo(2)
      paths(0).toFile.exists must beTrue
      paths(1).toFile.exists must beTrue
    }
  }
}
