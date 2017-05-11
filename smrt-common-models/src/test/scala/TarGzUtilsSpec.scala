import org.specs2.mutable.Specification
import com.pacbio.common.utils.TarGzUtils
import java.nio.file.{Files, Paths}

import org.apache.commons.io.FileUtils


class TarGzUtilsSpec extends Specification{


  "Sanity Test for reading and writing tar.gz files" should {
    "Write TGZ file and unzip " in {

      val name = "subdir-1"
      val fileName = "file.log"
      val content = Seq("log file", "log data").reduce(_ + "\n" + _)

      val t = Files.createTempDirectory("test")
      val ts = t.resolve("subdir-1")
      ts.toFile.mkdir()

      val f1 = t.resolve(fileName)
      val f2 = ts.resolve(fileName)

      FileUtils.writeStringToFile(f1.toFile, content)
      FileUtils.writeStringToFile(f2.toFile, content)

      val tgz = Files.createTempFile("test", "tar.gz")

      TarGzUtils.createTarGzip(t, tgz.toFile)

      val outDir = Files.createTempDirectory("test-tar-gz")
      TarGzUtils.uncompressTarGZ(tgz.toFile, outDir.toFile)

      val outSubDir = outDir.resolve(name)
      val outF1 = outDir.resolve(fileName)
      val outSubF1 = outSubDir.resolve(fileName)

      Files.exists(outSubDir) must beTrue
      Files.exists(outF1) must beTrue
      Files.exists(outSubF1) must beTrue

      FileUtils.readFileToString(outF1.toFile) must beEqualTo(content)
      FileUtils.readFileToString(outSubF1.toFile) must beEqualTo(content)


      // Cleanup
      FileUtils.deleteQuietly(t.toFile)
      FileUtils.deleteQuietly(tgz.toFile)

    }
  }

}
