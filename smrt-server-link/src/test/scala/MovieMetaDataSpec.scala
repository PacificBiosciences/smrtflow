import com.pacbio.secondary.smrtlink.analysis.legacy.MovieMetaDataXml
import org.specs2.mutable._

class MovieMetaDataSpec extends Specification {

  sequential
  "Load example-01 movie" should {
    "Movie parsing  sanity" in {
      val path = getClass.getResource(
        "movie-metadatas/example-01/m141015_104159_42161_c100721142550000001823146404301574_s1_p0.metadata.xml")
      val e = MovieMetaDataXml.loadFromUrl(path)
      e.cellId must beEqualTo("10072114255000000182314640430157.4")
    }
    "Parse reference with multiple contigs" in {
      val path = getClass.getResource(
        "movie-metadatas/m110106_050924_00114_c000000062550000000300000112311181_s2_p0.metadata.xml")
      val e = MovieMetaDataXml.loadFromUrl(path)
      e.cellId must beEqualTo("00000006255000000030000011231118.1")
    }
  }
}
