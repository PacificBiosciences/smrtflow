import java.nio.file.{Path, Paths}

/**
  * Please try to centralize the Test duplication utils here.
  */
trait PacBioTestUtils {

  def getResourcePath(name: String): Path =
    Paths.get(getClass.getResource(name).toURI)

}

object PacBioTestUtils extends PacBioTestUtils
