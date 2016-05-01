package com.pacbio.secondary.analysis.legacy

import scala.collection.JavaConversions._
import java.net.URL
import java.io.File
import scala.xml.Elem

/**
 * Need fields
 *  - sampleName (from Run/Name)
 *  - collectionPathUri
 *  - cellId = eightPacBarcode + cellName
 *  - primaryResultsFOlder relative to the metadata XML
 *  - secondary* fields
 *  - instrCtrlVersion
 *
 *
 *  Cell Id from EightPac/Barcode + '.' + CellIndex
 *  For example, 10072114255000000182314640430157.4
 * <CollectionProtocol>MagBead OneCellPerWell v1</CollectionProtocol>
 * <CollectionNumber>5</CollectionNumber>
 * <CellIndex>4</CellIndex>
 * <SetNumber>1</SetNumber>
 * <EightPac>
 *  <PartNumber>0018</PartNumber>
 *  <LotNumber>231464</LotNumber>
 *  <Barcode>10072114255000000182314640430157</Barcode>
 *  <ExpirationDate>2015-04-30</ExpirationDate>
 * </EightPac>
 *
 */

case class MovieMetaDataRecord(cellId: String,
                               nCells: Int,
                               pooledSampleName: String,
                               sampleName: String,
                               plateName: String,
                               instrumentId: String,
                               instrumentName: String,
                               runId: String,
                               runName: String,
                               collectionProtocol: String,
                               primaryVersion: String,
                               primaryProtocol: String,
                               collectionPath: String,
                               primaryResultsFolder: String)

object MovieMetaDataXml {

  def loadFromElement(root: Elem): MovieMetaDataRecord = {
    // These are necessary to compute the required values
    val barcode = (root \ "EightPac" \ "Barcode").text
    val cellIndex = (root \ "CellIndex").text

    val cellId = barcode + '.' + cellIndex
    val nCells = 1234
    val pooledSampleName = "pooledSampleName"
    val sampleName = (root \ "Sample" \ "Name").text
    // Is this correct?
    val plateName = (root \ "Sample" \ "PlateId").text
    val instrumentId = (root \ "InstrumentId").text
    val instrumentName = (root \ "InstrumentName").text
    val runId = (root \ "Run" \ "Id").text
    val runName = (root \ "Run" \ "Name").text
    val collectionProtocol = (root \ "CollectionProtocol").text
    // Is this the primary version
    val primaryVersion = (root \ "version").text
    val primaryProtocol = (root \ "Primary" \ "Protocol").text

    // These aren't used in Aaron's model
    val instControlVersion = (root \ "InstCtrlVer").text
    val collectionPathUri = (root \ "Primary" \ "CollectionPathUri").text
    val primaryResultsFolder = (root \ "Primary" \ "ResultsFolder").text

    MovieMetaDataRecord(cellId, nCells, pooledSampleName, sampleName, instrumentId, instrumentId, runId, runName,
      collectionProtocol, primaryVersion, primaryProtocol, collectionPathUri, primaryResultsFolder, instControlVersion)
  }

  def loadFromFile(path: File): MovieMetaDataRecord = loadFromElement(scala.xml.XML.loadFile(path))
  def loadFromUrl(url: URL): MovieMetaDataRecord = loadFromElement(scala.xml.XML.load(url))


}