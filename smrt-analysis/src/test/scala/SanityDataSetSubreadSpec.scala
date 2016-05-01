import java.nio.file.DirectoryStream.Filter
import java.nio.file.{Path, Files, Paths}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import com.pacbio.secondary.analysis.datasets.io.{DataSetValidator, DataSetJsonUtils, DataSetLoader}
import com.pacificbiosciences.pacbiodatasets._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.specs2.mutable.Specification

import scala.util.Try

/**
 * Sannity test to load dataset test files and XML -> DataSet Object
 * Created by mkocher on 5/29/15.
 */
class SanityDataSetSubreadSpec extends Specification with LazyLogging {

  sequential

  val UNKNOWN = "unknown"

  val ROOT_DIR = "/dataset-subreads"

  def sanitySubreadSet(dataset: SubreadSet): Boolean = {

    val name = Try {dataset.getName} getOrElse UNKNOWN
    val dsVersion = Try {dataset.getVersion} getOrElse "0.0.0"
    val tags = Try {dataset.getTags} getOrElse "converted"
    val wellSampleName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getName } getOrElse UNKNOWN
    // This might not be correct. Should the description come from the Collection Metadata
    val comments = Try { dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getDescription } getOrElse " "

    // Plate Id doesn't exist, but keeping it so I don't have to update the db schema
    val cellIndex = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getCellIndex.toInt } getOrElse -1
    val wellName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getWellSample.getWellName } getOrElse UNKNOWN
    val runName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getRunDetails.getName } getOrElse UNKNOWN
    val contextId = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getContext } getOrElse UNKNOWN
    val instrumentName = Try { dataset.getDataSetMetadata.getCollections.getCollectionMetadata.head.getInstrumentName } getOrElse UNKNOWN

    val bioSampleName = Try { dataset.getDataSetMetadata.getBioSamples.getBioSample.head.getName } getOrElse UNKNOWN

    val numRecords = Try { dataset.getDataSetMetadata.getNumRecords} getOrElse 0
    val totalLength = Try {dataset.getDataSetMetadata.getTotalLength } getOrElse 0L

    logger.info(s"BioSample $bioSampleName Des '$comments' tags $tags Wellname $wellName Sample name $wellSampleName Cell Index $cellIndex")
    true
  }

  def loadDs(path: Path): SubreadSet = {
    logger.info(s"loading subread datasets from $path")
    val ds = DataSetLoader.loadSubreadSet(path)
    DataSetValidator.validate(ds, path.getParent)
    logger.info(s"successfully loaded subread dataset $ds")
    //val jstring = DataSetJsonUtils.subreadSetToJson(ds)
    //println(s"HdfSubread json $jstring")
    ds
  }

  "Sanity DataSet Loading" should {

    "Subread dataset loading /dataset-subreads" in {

      val root = getClass.getResource(ROOT_DIR)
      val p = Paths.get(root.toURI)
      val files = p.toFile.listFiles.filter(_.isFile).toList
      logger.info(s"Loading datasets from $p")
      val datasets = files.map(x => loadDs(x.toPath))
      val isValid = datasets.map(x => sanitySubreadSet(x))
      true must beTrue
    }

    "Load SubreadSet from ICS" in {
      val name = "/dataset-subreads/m54008_160215_180009.subreadset.xml"

      val ux = getClass.getResource(name)
      val ds = loadDs(Paths.get(ux.toURI))
      val isValid = sanitySubreadSet(ds)
      true must beTrue
    }

  }

}
