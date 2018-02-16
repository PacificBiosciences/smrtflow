package com.pacbio.secondary.smrtlink.analysis.converters

import java.io.FileOutputStream
import java.nio.file.{Files, Path, Paths}
import javax.xml.transform.stream.{StreamResult, StreamSource}
import javax.xml.transform.{Result, Source, Transformer}

import com.pacbio.secondary.smrtlink.analysis.datasets.HdfSubreadSetIO
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetLoader,
  DataSetMerger,
  DataSetWriter
}
import com.pacificbiosciences.pacbiodatasets.HdfSubreadSet
import com.typesafe.scalalogging.LazyLogging
import net.sf.saxon.TransformerFactoryImpl
import org.apache.commons.io.{FileUtils, FilenameUtils}

import scala.util.Try

/**
  * Converts a movie Metadata to HdfSubread DataSet
  *
  * Created by mkocher on 9/26/15.
  */
object MovieMetadataConverter extends LazyLogging {

  // Default Name used in the SubreadSet if one is not
  // provided
  val DATASET_SUBREAD_DEFAULT_NAME = "DataSet_SubreadSet"
  // Path to RS movie -> HdfSubreadSet XML
  val DATASET_SUBREAD_XSLT = "/HdfSubreadDatasetTransform.xslt"

  private def buildTransformer(xslSource: Source): Transformer = {
    val transformerFactory = new TransformerFactoryImpl()
    transformerFactory.newTransformer(xslSource)
  }

  // Convert by loading Resource as a Stream (for jar file, but doesn't work for tests)
  private def convertFromStream(in: Path, out: Path): Path = {
    val xmlSource: Source = new StreamSource(in.toFile)

    val result: Result = new StreamResult(out.toFile)

    // FIXME There's problems loading resources with this method. Tests pass
    // but jar files with assembly and pack are broken
    val aStream = getClass.getClass.getResourceAsStream(DATASET_SUBREAD_XSLT)

    logger.debug(s"Loading xsl from $aStream")
    val streamSource = new StreamSource(aStream)
    val transformer = buildTransformer(streamSource)
    transformer.transform(xmlSource, result)
    out
  }

  // Convert by loading XSL as a Resource
  private def convertFromURI(in: Path, out: Path): Path = {

    // Movie Metadata XML
    val xmlSource: Source = new StreamSource(in.toFile)

    val outputStream = new FileOutputStream(out.toFile)

    val result: Result = new StreamResult(outputStream)

    // FIXME There's problems loading resources with this method. Tests pass but jar files with assembly and pack are broken
    val myXsl = getClass.getResource(DATASET_SUBREAD_XSLT)
    val myXslPath = myXsl.getPath
    val xslSource: Source = new StreamSource(myXslPath)
    val transformer = buildTransformer(xslSource)
    transformer.transform(xmlSource, result)
    out
  }

  /**
    * Convert RS-era MovieMeta XML file -> HdfSubread Dataset XML file
    *
    * @param path Path to a dataset XML file
    * @return
    */
  private def convertMovieMetaDataToSubread(
      path: Path,
      output: Path): Either[DatasetConvertError, HdfSubreadSet] = {

    logger.debug(
      s"attempting to convert ${path.toString} to Dataset ${output.toString}")

    def convertAndLoad(
        f: (Path, Path) => Unit): (Path, Path) => HdfSubreadSet = {
      (in, out) =>
        f(in, out)
        DataSetLoader.loadHdfSubreadSet(out)
    }

    // The tests can't load from a Stream and jar files
    // can't load resources from URIs.
    // Failed to load the XSL in the recover
    val tx = Try(convertAndLoad(convertFromStream)(path, output))
      .recover { case _ => convertAndLoad(convertFromURI)(path, output) }

    tx.toEither.left.map { t =>
      Files.deleteIfExists(output)
      DatasetConvertError(t.getMessage)
    }

  }

  /**
    * Convert a list of RS-era Movie Metadata XML files to an HdfSubreadSet
    *
    * If there are more than one file, they will be merged into a Single HdfSubreadSet
    * and written to the configured output file.
    *
    * @param paths
    * @return
    */
  private def convertMovieMetadatasToHdfSubreadSet(
      paths: Set[Path],
      out: Path,
      dsName: String): Either[DatasetConvertError, HdfSubreadSetIO] = {

    val xs = paths.toList.map { p =>
      val tmpOut =
        Files.createTempFile("rs-converted-dataset", ".hdfsubreadset.xml")
      convertRsMovieToHdfSubreadSet(p, tmpOut, "temp-dataset")
    }

    val hsets = xs.flatMap(_.right.toOption)
    hsets.map(_.path.toFile).foreach(FileUtils.deleteQuietly)

    val errors = xs.flatMap(_.left.toOption)
    if (errors.nonEmpty) {
      Left(DatasetConvertError(s"Failed to convert $errors"))
    } else {
      val hset =
        DataSetMerger.mergeHdfSubreadSets(hsets.map(_.dataset), dsName)
      DataSetWriter.writeHdfSubreadSet(hset, out)
      Right(HdfSubreadSetIO(hset, out))
    }
  }

  def convertRsMovieToHdfSubreadSet(
      path: Path,
      out: Path,
      dsName: String): Either[DatasetConvertError, HdfSubreadSetIO] = {
    convertMovieMetaDataToSubread(path, out).map { hset =>
      hset.setName(dsName)
      DataSetWriter.writeHdfSubreadSet(hset, out)
      HdfSubreadSetIO(hset, out)
    }
  }

  /**
    * Convert Movie Metadata XML or FOFN of Movie Metadata XML files to HdfSubreadSet
    * @param movie
    * @return
    */
  def convertMovieOrFofnToHdfSubread(
      movie: Path,
      out: Path,
      dsName: String): Either[DatasetConvertError, HdfSubreadSetIO] = {
    FilenameUtils.getExtension(movie.getFileName.toString) match {
      case "fofn" =>
        convertMovieMetadatasToHdfSubreadSet(Utils.fofnToFiles(movie).toSet,
                                             out,
                                             dsName)
      case "xml" =>
        convertRsMovieToHdfSubreadSet(movie, out, dsName)
      case x =>
        Left(DatasetConvertError(
          s"Unsupported file type. '$x'. Supported files types 'xml', 'fofn'"))
    }
  }

}
