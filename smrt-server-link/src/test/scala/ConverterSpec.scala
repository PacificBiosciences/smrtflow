import java.nio.file.{Paths, Files}
import com.pacbio.secondary.smrtlink.analysis.converters.MovieMetadataConverter._
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import collection.JavaConverters._

/**
  * Created by mkocher on 3/16/15.
  *
  * Spec to test the conversion of files to DataSets
  */
class ConverterSpec extends Specification with LazyLogging {

  sequential

  "Convert movie.metadata.xml to SubreadDataSet.xml" should {
    "Sanity Movie to H5 dataset conversion" in {
      val name = "tmp.h5.dataset.xml"
      val dsXml = Files.createTempFile("", name)

      val uri = getClass.getResource(
        "movie-metadatas/example-01/m141015_104159_42161_c100721142550000001823146404301574_s1_p0.metadata.xml")
      logger.debug(
        s"Converting movie to dataset ${uri.toString} -> ${dsXml.toString}")
      val path = Paths.get(uri.toURI)
      val x = convertMovieMetaDataToSubread(path)
      x.isRight must beTrue
      val xs =
        x.right.get.getExternalResources.getExternalResource.asScala.toList.length
      xs mustEqual 3
    }
  }

}
