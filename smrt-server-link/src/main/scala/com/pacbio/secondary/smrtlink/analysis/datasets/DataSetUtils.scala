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

trait DataSetMetadataUtils {

  protected def getCollectionsMetadata(
      ds: ReadSetType): Option[Seq[XsdMetadata]] =
    Try {
      Option(ds.getDataSetMetadata.getCollections.getCollectionMetadata)
    }.getOrElse(None).map(_.toSeq)

  protected def getWellSamples(ds: ReadSetType): Try[Seq[WellSample]] = Try {
    getCollectionsMetadata(ds)
      .map(_.map(md => md.getWellSample))
      .getOrElse(Seq.empty[WellSample])
  }

  private def getNames(entities: Option[Seq[BaseEntityType]]): Seq[String] =
    entities.map(e => e.map(_.getName)).getOrElse(Seq.empty[String]).sorted

  protected def getWellSampleNames(ds: ReadSetType): Seq[String] =
    getNames(getWellSamples(ds).toOption)

  private def getWellBioSamples(ws: WellSample): Option[Seq[BioSampleType]] = {
    Try {
      Option(ws.getBioSamples.getBioSample).map(_.toSeq)
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
            Option(bs.getDNABarcodes.getDNABarcode).map(_.toSeq)
          }.toOption.getOrElse(None)))
        .map(_.map(bc => bc.getOrElse(Seq.empty[DNABarcode])).flatten))

  private def getWellSample(ds: ReadSetType): Try[WellSample] = Try {
    getWellSamples(ds).toOption
      .map { w =>
        if (w.size > 1) {
          throw new RuntimeException(
            s"multiple well sample records are present")
        } else if (w.size == 0) {
          throw new RuntimeException(s"no well sample records are present")
        } else {
          w.head
        }
      }
      .getOrElse {
        throw new RuntimeException("sample metadata missing or corrupt")
      }
  }

  protected def setBioSampleName(ds: ReadSetType, name: String): Try[String] = {
    for {
      ws <- getWellSample(ds)
      msg <- Try {
        getWellBioSamples(ws) match {
          case Some(bs) => {
            if (bs.size > 1) {
              throw new RuntimeException("Multiple BioSample records present")
            } else {
              bs.head.setName(name)
            }
          }
          case None => {
            val bs = new BioSampleType()
            bs.setName(name)
            val bss = new BioSamples()
            bss.getBioSample.add(bs)
            ws.setBioSamples(bss)
          }
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
