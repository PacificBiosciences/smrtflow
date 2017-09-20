package com.pacbio.secondary.smrtlink.analysis.datasets

import scala.util.Try
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

  protected def getCollectionsMetadata(
      ds: ReadSetType): Option[Seq[XsdMetadata]] =
    Try {
      Option(ds.getDataSetMetadata.getCollections.getCollectionMetadata)
    }.getOrElse(None).map(_.toList)

  protected def getWellSamples(ds: ReadSetType): Try[Seq[WellSample]] = Try {
    getCollectionsMetadata(ds)
      .map(_.map(md => md.getWellSample).toList)
      .getOrElse(Seq.empty[WellSample])
  }

  private def getNames(entities: Option[Seq[BaseEntityType]]): Seq[String] =
    entities.map(e => e.map(_.getName)).getOrElse(Seq.empty[String]).sorted

  protected def getWellSampleNames(ds: ReadSetType): Seq[String] =
    getNames(getWellSamples(ds).toOption)

  private def getWellBioSamples(ws: WellSample): Option[Seq[BioSampleType]] = {
    Try {
      Option(ws.getBioSamples.getBioSample).map(_.toList)
    }.toOption.getOrElse(None)
  }

  protected def getBioSamples(ds: ReadSetType): Try[Seq[BioSampleType]] = Try {
    getWellSamples(ds).toOption
      .map(_.map(ws => getWellBioSamples(ws)))
      .map(_.map(bs => bs.getOrElse(Seq.empty[BioSampleType])).flatten)
      .getOrElse(Seq.empty[BioSampleType])
  }

  protected def getBioSampleNames(ds: ReadSetType): Seq[String] =
    getNames(getBioSamples(ds).toOption)

  protected def getDnaBarcodeNames(ds: ReadSetType): Seq[String] =
    getNames(
      getBioSamples(ds).toOption
        .map(_.map(bs =>
          Try {
            Option(bs.getDNABarcodes.getDNABarcode).map(_.toList)
          }.toOption.getOrElse(None)))
        .map(_.map(bc => bc.getOrElse(Seq.empty[DNABarcode])).flatten))

  protected def getWellSample(ds: ReadSetType): Try[WellSample] = Try {
    getWellSamples(ds).toOption
      .map { ws =>
        ws match {
          case Nil =>
            throw new RuntimeException(s"no well sample records are present")
          case value :: Nil => value
          case value :: tail =>
            throw new RuntimeException(
              s"multiple well sample records are present")
          case _ => throw new RuntimeException("Unexpected case")
        }
      }
      .getOrElse {
        throw new RuntimeException("sample metadata missing or corrupt")
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
        getWellBioSamples(ws) match {
          case Some(bs) =>
            bs match {
              case Nil => setBioSample(ws, name)
              case value :: Nil => value.setName(name)
              case value :: tail =>
                throw new RuntimeException(
                  "Multiple BioSample records present")
            }
          case None => setBioSample(ws, name)
        }
        s"Set BioSample name to $name"
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
