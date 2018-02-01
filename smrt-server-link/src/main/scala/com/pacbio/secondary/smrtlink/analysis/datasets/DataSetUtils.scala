package com.pacbio.secondary.smrtlink.analysis.datasets

import java.nio.file.Path

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet
import com.typesafe.scalalogging.LazyLogging

import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiodatasets.{DataSetType => XsdDataSetType}
import com.pacificbiosciences.pacbiobasedatamodel.{
  BaseEntityType,
  DNABarcode,
  ExternalResource,
  ExternalResources,
  FilterType
}
import com.pacificbiosciences.pacbiocollectionmetadata.{
  WellSample,
  CollectionMetadata => XsdMetadata
}
import com.pacificbiosciences.pacbiosampleinfo.{BioSampleType, BioSamples}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetLoader,
  DataSetWriter
}

import collection.JavaConverters._

/**
  * Utilities for accessing and manipulating dataset metadata items,
  * especially sample info
  */
trait DataSetMetadataUtils extends LazyLogging {

  val UNKNOWN = "unknown"
  val MULTIPLE_SAMPLES_NAME = "[multiple]"

  /**
    * Recurrsively return a flattened list of ExternalResource instances
    *
    * This will also handle null of ExternalResource entities.
    *
    * @param exs ExternalResources (can be Null)
    */
  def getAllExternalResources(exs: ExternalResources): Seq[ExternalResource] = {
    Option(exs) match {
      case Some(xs) =>
        xs.getExternalResource.asScala.toList.flatMap { e =>
          Option(e)
            .map(i => Seq(i))
            .getOrElse(Nil) ++ getAllExternalResources(e.getExternalResources)
        }
      case _ => Nil
    }
  }

  protected def getCollectionsMetadata(ds: ReadSetType): Seq[XsdMetadata] =
    Try {
      Option(ds.getDataSetMetadata.getCollections.getCollectionMetadata)
        .map(_.asScala.toList)
        .getOrElse(Seq.empty[XsdMetadata])
    }.toOption.getOrElse(Seq.empty[XsdMetadata])

  protected def getWellSamples(ds: ReadSetType): Seq[WellSample] = {
    getCollectionsMetadata(ds)
      .map(md => md.getWellSample)
      .toList
  }

  /**
    * Returns a list of unique WellSample names
    */
  protected def getWellSampleNames(ds: ReadSetType): Seq[String] =
    getWellSamples(ds).map(_.getName).sorted.distinct

  private def getWellBioSamples(ws: WellSample): Seq[BioSampleType] = {
    Try {
      Option(ws.getBioSamples.getBioSample)
        .map(_.asScala.toList)
        .getOrElse(Seq.empty[BioSampleType])
    }.toOption.getOrElse(Seq.empty[BioSampleType])
  }

  protected def getBioSamples(ds: ReadSetType): Seq[BioSampleType] = {
    getWellSamples(ds)
      .map(ws => getWellBioSamples(ws))
      .flatten
  }

  /**
    * Returns a list of unique BioSample names
    */
  protected def getBioSampleNames(ds: ReadSetType): Seq[String] =
    getBioSamples(ds).map(_.getName).sorted.distinct

  private def getBioSampleBarcodes(bs: BioSampleType): Seq[DNABarcode] =
    Try {
      Option(bs.getDNABarcodes.getDNABarcode)
        .map(_.asScala.toList)
        .getOrElse(Seq.empty[DNABarcode])
    }.toOption.getOrElse(Seq.empty[DNABarcode])

  protected def getDnaBarcodeNames(ds: ReadSetType): Seq[String] = {
    getBioSamples(ds)
      .map(bs => getBioSampleBarcodes(bs))
      .flatten
      .map(_.getName)
      .sorted
      .distinct
  }

  protected def getWellSample(ds: ReadSetType): Try[WellSample] = Try {
    getWellSamples(ds) match {
      case Nil =>
        throw new RuntimeException(s"no well sample records are present")
      case value :: Nil => value
      case value :: tail =>
        throw new RuntimeException(s"multiple well sample records are present")
    }
  }

  private def setBioSample(ws: WellSample, name: String) = {
    val bs = new BioSampleType()
    bs.setName(name)
    val bss = new BioSamples()
    bss.getBioSample.add(bs)
    ws.setBioSamples(bss)
  }

  private def setBioSampleName(ws: WellSample, name: String) = {
    getWellBioSamples(ws) match {
      case Nil => setBioSample(ws, name)
      case value :: Nil => value.setName(name)
      case value :: tail => throw new RuntimeException("multiple BioSamples")
    }
  }

  /**
    * Set the BioSample name to the specified value.  This will fail if there
    * are no WellSamples present, or if there are already multiple unique
    * BioSample names present.  It will however insert BioSample records
    * where they are not already present.  (It is also insensitive to the
    * names of WellSamples, which do not need to be unique here.)
    *
    * @param ds SubreadSet or related type
    * @param name new BioSample name
    * @param enforceUniqueness if false, this will ignore the presence of
    *                          multiple unique BioSample names
    * @return success message
    */
  protected def setBioSampleName(
      ds: ReadSetType,
      name: String,
      enforceUniqueness: Boolean = true): Try[String] = Try {
    getWellSamples(ds) match {
      case Nil =>
        throw new RuntimeException(s"no well sample records are present")
      case (ws: Seq[WellSample]) => {
        val bioSampleNames = getBioSampleNames(ds)
        def doUpdate = ws.map(s => setBioSampleName(s, name))
        val nRecords: Long = (bioSampleNames match {
          case Nil => doUpdate
          case value :: Nil => doUpdate
          case value :: tail =>
            if (enforceUniqueness) {
              throw new RuntimeException(
                "Multiple unique BioSample names already present")
            } else doUpdate
        }).size
        s"Set $nRecords BioSample tag name(s) to $name"
      }
    }
  }

  /**
    * Set the WellSample name to the specified value.  This will fail if there
    * are no WellSamples present, or if there are already multiple unique
    * WellSample names present (unless enforceUniqueness is false).
    *
    * @param ds SubreadSet or related type
    * @param name new WellSample name
    * @param enforceUniqueness if false, this will ignore the presence of
    *                          multiple unique Wellample names
    * @return success message
    */
  protected def setWellSampleName(
      ds: ReadSetType,
      name: String,
      enforceUniqueness: Boolean = true): Try[String] = Try {
    getWellSamples(ds) match {
      case Nil =>
        throw new RuntimeException(s"no well sample records are present")
      case (ws: Seq[WellSample]) => {
        val wellSampleNames = ws.map(_.getName).sorted.distinct
        def doUpdate = ws.map(_.setName(name))
        val nRecords: Long = (wellSampleNames match {
          case Nil => doUpdate
          case value :: Nil => doUpdate
          case value :: tail =>
            if (enforceUniqueness) {
              throw new RuntimeException(
                "Multiple unique WellSample names already present")
            } else doUpdate
        }).size
        s"Set $nRecords WellSample tag name(s) to $name"
      }
    }
  }

  /**
    * Return true if the dataset meets the conditions allowing the WellSample
    * name to be set in the XML file.
    */
  protected def canEditWellSampleName(ds: ReadSetType): Boolean = {
    getWellSamples(ds) match {
      case Nil => false
      case (ws: Seq[WellSample]) => ws.map(_.getName).sorted.distinct.size == 1
    }
  }

  /**
    * Return true if the dataset meets the conditions allowing the BioSample
    * name to be set in the XML file.
    */
  protected def canEditBioSampleName(ds: ReadSetType): Boolean = {
    getWellSamples(ds) match {
      case Nil => false
      case (ws: Seq[WellSample]) => getBioSampleNames(ds).size <= 1
    }
  }
}

object DataSetMetadataUtils extends DataSetMetadataUtils

/**
  * Convenience methods for applying metadata fields from the SMRT Link
  * database to an XML dataset.
  */
object DataSetUpdateUtils extends DataSetMetadataUtils {

  /**
    * Apply an optional update, with error handling
    * @return None if successful, or Some(error)
    */
  private def setNameIfDefined(
      sampleName: Option[String],
      fx: (String) => (Try[String])): Option[String] = {
    sampleName
      .flatMap { name =>
        name match {
          case UNKNOWN | MULTIPLE_SAMPLES_NAME => None
          case x => Some(fx(name))
        }
      }
      .flatMap { result =>
        result match {
          case Success(msg) => None
          case Failure(err) => Some(err.getMessage)
        }
      }
  }

  /**
    * Apply metadata updates as needed.
    * @return None if updates were successful or skipped, or Some(msg) if an
    *         error occurred
    */
  def applyMetadataUpdates(
      ds: ReadSetType,
      bioSampleName: Option[String] = None,
      wellSampleName: Option[String] = None): Option[String] = {
    Seq(
      setNameIfDefined(wellSampleName,
                       (name: String) => setWellSampleName(ds, name)),
      setNameIfDefined(bioSampleName,
                       (name: String) => setBioSampleName(ds, name))
    ).flatten match {
      case Nil => None
      case (msgs: Seq[String]) => {
        val msg =
          s"Error(s) occurred applying metadata updates: ${msgs.mkString("; ")}"
        logger.warn(msg)
        Some(msg)
      }
    }
  }

  /**
    * Write a copy of a dataset to disk, applying metadata updates as needed.
    * This only works for SubreadSets at present.  Does not fail if the updates
    * are unsuccessful, but will return the error message(s).
    *
    * @return Some(error) if metadata updates failed, otherwise None
    */
  def saveUpdatedCopy(dsFile: Path,
                      outputFile: Path,
                      bioSampleName: Option[String] = None,
                      wellSampleName: Option[String] = None,
                      resolvePaths: Boolean = true): Option[String] = {
    val ds = if (resolvePaths) {
      DataSetLoader.loadAndResolveSubreadSet(dsFile)
    } else {
      DataSetLoader.loadSubreadSet(dsFile)
    }
    logger.info(s"Saving updated dataset XML to $outputFile")
    val errors = applyMetadataUpdates(ds, bioSampleName, wellSampleName)
    DataSetWriter.writeDataSet(DataSetMetaTypes.Subread, ds, outputFile)
    errors
  }

  /**
    * Load a SubreadSet (without resolving resource paths) and attempt to
    * apply the specified metadata changes without writing them to disk.
    *
    * @return Some(error) if unsuccessful, otherwise None
    */
  def testApplyEdits(dsFile: Path,
                     bioSampleName: Option[String] = None,
                     wellSampleName: Option[String] = None): Option[String] = {
    val ds = DataSetLoader.loadSubreadSet(dsFile)
    applyMetadataUpdates(ds, bioSampleName, wellSampleName)
  }
}

trait DataSetFilterUtils {
  // extracted from pbcore
  private val VALID_OPS = TreeSet("==",
                                  "=",
                                  "eq",
                                  "!=",
                                  "ne",
                                  ">=",
                                  "&gt;=",
                                  "gte",
                                  "<=",
                                  "&lt;=",
                                  "lte",
                                  ">",
                                  "&gt;",
                                  "gt",
                                  "<",
                                  "&lt;",
                                  "lt",
                                  "in",
                                  "not_in",
                                  "&",
                                  "~")

  private val VALID_NAMES = TreeSet(
    "rname",
    "length",
    "qstart",
    "qend",
    "qname",
    "qid",
    "movie",
    "zm",
    "bc",
    "bcr",
    "bcf",
    "bcq",
    "bq",
    "qs",
    "rq",
    "pos",
    "tstart",
    "tend",
    "accuracy",
    "readstart",
    "cx",
    "n_subreads",
    "mapqv"
  )

  private def toOpsList = "'" ++ VALID_OPS.mkString("', '") ++ "'"
  private def toNamesList = "'" ++ VALID_NAMES.mkString("', '") ++ "'"

  def clearFilters(ds: XsdDataSetType): XsdDataSetType.Filters = {
    val f = new XsdDataSetType.Filters()
    ds.setFilters(f)
    f
  }

  def addFilter(ds: XsdDataSetType,
                name: String,
                operator: String,
                value: String) = {
    if (!VALID_NAMES.contains(name)) {
      throw new IllegalArgumentException(
        s"'$name' is not a valid dataset filter criterion (allowed names: ${toNamesList})")
    }
    if (!VALID_OPS.contains(operator)) {
      throw new IllegalArgumentException(
        s"The operator '$operator' is not recognized (valid operators: ${toOpsList})")
    }
    val newFilter = new FilterType()
    val newProps = new FilterType.Properties()
    val prop = new FilterType.Properties.Property()
    prop.setName(name)
    prop.setOperator(operator)
    prop.setValue(value)
    newProps.getProperty.add(prop)
    newFilter.setProperties(newProps)
    Option(ds.getFilters)
      .getOrElse(clearFilters(ds))
      .getFilter
      .add(newFilter)
  }

  def addFilter(ds: XsdDataSetType, rule: DataSetFilterRule): Unit =
    addFilter(ds, rule.name, rule.operator, rule.value)

  def addLengthFilter(ds: XsdDataSetType,
                      value: Int,
                      operator: String = ">=") =
    addFilter(ds, "length", operator, value.toString)

}
