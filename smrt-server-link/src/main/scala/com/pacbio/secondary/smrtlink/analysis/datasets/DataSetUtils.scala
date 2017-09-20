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

  protected def getWellSampleNames(ds: ReadSetType): Seq[String] =
    getWellSamples(ds).map(_.getName).sorted

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

  protected def getBioSampleNames(ds: ReadSetType): Seq[String] =
    getBioSamples(ds).map(_.getName).sorted

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

  protected def setBioSampleName(ds: ReadSetType, name: String): Try[String] = {
    for {
      ws <- getWellSample(ds)
      msg <- Try {
        val msg = s"Set BioSample name to $name"
        getWellBioSamples(ws) match {
          case Nil => setBioSample(ws, name); msg
          case value :: Nil => value.setName(name); msg
          case value :: tail =>
            throw new RuntimeException("Multiple BioSample records present")
        }
      }
    } yield msg
  }

  protected def setWellSampleName(ds: ReadSetType, name: String): Try[String] = {
    for {
      ws <- getWellSample(ds)
      msg <- Try {
        ws.setName(name)
        s"Set WellSample name to $name"
      }
    } yield msg
  }
}
