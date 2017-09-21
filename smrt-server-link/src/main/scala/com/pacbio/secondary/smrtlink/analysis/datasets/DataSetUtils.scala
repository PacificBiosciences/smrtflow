package com.pacbio.secondary.smrtlink.analysis.datasets

import scala.util.{Try, Failure}
import scala.collection.JavaConversions._

import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiobasedatamodel.{BaseEntityType, DNABarcode}
import com.pacificbiosciences.pacbiocollectionmetadata.{
  CollectionMetadata => XsdMetadata,
  WellSample
}
import com.pacificbiosciences.pacbiosampleinfo.{BioSamples, BioSampleType}

/**
  * Utilities for accessing and manipulating dataset metadata items,
  * especially sample info
  */
trait DataSetMetadataUtils {

  protected def getCollectionsMetadata(ds: ReadSetType): Seq[XsdMetadata] =
    Try {
      Option(ds.getDataSetMetadata.getCollections.getCollectionMetadata)
        .map(_.toList)
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
        .map(_.toList)
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
        .map(_.toList)
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
      case ws: Seq[WellSample] => {
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
      case ws: Seq[WellSample] => {
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
}
