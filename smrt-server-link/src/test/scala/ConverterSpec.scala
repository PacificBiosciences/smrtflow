import java.nio.file.{Files, Paths}

import com.pacbio.secondary.smrtlink.analysis.converters.MovieMetadataConverter
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
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
      val tmpFile = Files.createTempFile("hdfsubreadset", ".hdfsubreadset.xml")
      val x =
        MovieMetadataConverter.convertRsMovieToHdfSubreadSet(path,
                                                             tmpFile,
                                                             "TestDataSet")
      x.isRight must beTrue

      val hset = DataSetLoader.loadHdfSubreadSet(tmpFile)

      hset.getExternalResources.getExternalResource.asScala.toList.length === 3

      FileUtils.deleteQuietly(tmpFile.toFile)

      val xs =
        x.right.get.dataset.getExternalResources.getExternalResource.asScala.toList.length
      xs mustEqual 3
    }
  }

}
