package com.pacbio.secondary.analysis.converters

import java.io.FileOutputStream
import java.nio.file.{Paths, Files, Path}
import javax.xml.transform.stream.{StreamResult, StreamSource}
import javax.xml.transform.{Result, Transformer, Source}

import com.pacbio.secondary.analysis.datasets.io.{DataSetMerger, DataSetLoader}
import com.pacificbiosciences.pacbiodatasets.HdfSubreadSet
import com.typesafe.scalalogging.LazyLogging
import net.sf.saxon.TransformerFactoryImpl
import org.apache.commons.io.FilenameUtils

import scala.io.{Source => SSource}

/**
 * Converts a movie Metadata to HdfSubread DataSet
 *
 * Created by mkocher on 9/26/15.
 */
object MovieMetadataConverter extends LazyLogging{

  // Default Name used in the SubreadSet if one is not
  // provided
  val DATASET_SUBREAD_DEFAULT_NAME = "DataSet_SubreadSet"
  // Path to RS movie -> HdfSubreadSet XML
  val DATASET_SUBREAD_XSLT = "/HdfSubreadDatasetTransform.xslt"

  /**
   * Convert RS-era MovieMeta XML file -> HdfSubread Dataset XML file
   *
   * @param path Path to a dataset XML file
   * @return
   */
  def convertMovieMetaDataToSubread(path: Path): Either[DatasetConvertError, HdfSubreadSet] = {

    val dsPath = Files.createTempFile("tmp", "hdfsubreadset.xml")
    logger.debug(s"attempting to convert ${path.toString} to Dataset ${dsPath.toString}")

    def buildTransformer(xslSource: Source): Transformer = {
      val transformerFactory = new TransformerFactoryImpl()
      transformerFactory.newTransformer(xslSource)
    }

    // Convert by loading Resource as a Stream (for jar file, but doesn't work for tests)
    def convertFromStream: Path = {
      val xmlSource: Source = new StreamSource(path.toFile)

      val result: Result = new StreamResult(dsPath.toFile)

      // FIXME There's problems loading resources with this method. Tests pass but jar files with assembly and pack are broken
      val aStream = getClass.getClass.getResourceAsStream(DATASET_SUBREAD_XSLT)

      logger.debug(s"Loading xsl from $aStream")
      val streamSource = new StreamSource(aStream)
      val transformer = buildTransformer(streamSource)
      transformer.transform(xmlSource, result)
      dsPath
    }

    // Convert by loading XSL as a Resource
    def convertFromURI: Path = {
      val outputStream = new FileOutputStream(dsPath.toFile)

      // Movie Metadata XML
      val xmlSource: Source = new StreamSource(path.toFile)

      val result: Result = new StreamResult(outputStream)

      // FIXME There's problems loading resources with this method. Tests pass but jar files with assembly and pack are broken
      val myXsl = getClass.getResource(DATASET_SUBREAD_XSLT)
      val myXslPath = myXsl.getPath
      val xslSource: Source = new StreamSource(myXslPath)
      val transformer = buildTransformer(xslSource)
      transformer.transform(xmlSource, result)
      dsPath
    }

    // FIXME. This is lackluster code. The tests can't load from a Stream and jar files can't load resources from URIs.
    try {
      convertFromStream
      logger.info(s"Completed writing dataset to $dsPath")
      Right(DataSetLoader.loadHdfSubreadSet(dsPath))
    } catch {
      case e: Exception =>
        // Failed to load the XSL
        //logger.warn("Failed to load XSL from stream. Trying to load as URI")
        try {
          //logger.debug("Attempting to load XSL from resource.")
          convertFromURI
          logger.info(s"Completed writing dataset to $dsPath")
          Right(DataSetLoader.loadHdfSubreadSet(dsPath))
        } catch {
          case e: Exception =>
            logger.error(s"Failed to convert $path")
            Files.deleteIfExists(dsPath)
            Left(DatasetConvertError(e.getMessage))
        }
    }
  }
  /**
   * Convert a list of RS-era Movie Metadata XML files to an HdfSubreadSet
   *
   * @param paths
   * @return
   */
  def convertMovieMetadatasToHdfSubreadSet(paths: Seq[Path]): Either[DatasetConvertError, HdfSubreadSet] = {

    val xs = paths.map(x => convertMovieMetaDataToSubread(x))
    val subreadSets = xs.flatMap(_.right.toOption)
    val errors = xs.flatMap(_.left.toOption)
    if (errors.nonEmpty) {
      Left(DatasetConvertError(s"Failed to convert $errors"))
    } else {
      Right(DataSetMerger.mergeHdfSubreadSets(subreadSets, "Convert-movie"))
    }
  }

  /**
   * Convert Movie Metadata XML or FOFN of Movie Metadata XML files to HdfSubreadSet
   * @param movie
   * @return
   */
  def convertMovieOrFofnToHdfSubread(movie: String): Either[DatasetConvertError, HdfSubreadSet] = {
    FilenameUtils.getExtension(movie) match {
      case "fofn" => convertMovieMetadatasToHdfSubreadSet(Utils.fofnToFiles(Paths.get(movie)).toSet.toList)
      case "xml" => convertMovieMetadatasToHdfSubreadSet(Seq(Paths.get(movie)))
      case x => Left(DatasetConvertError(s"Unsupported file type. '$x'. Supported files types 'xml', 'fofn'"))
    }
  }

  
}
