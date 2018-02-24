package com.pacbio.secondary.smrtlink.models

import java.io.ByteArrayInputStream
import java.nio.file.{Paths, Path}
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.bind.{Unmarshaller, JAXBContext}
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.time.PacBioDateTimeFormat
import com.pacificbiosciences.pacbiobasedatamodel.{
  RecordedEventType,
  SupportedAcquisitionStates
}
import com.pacificbiosciences.pacbiodatamodel.PacBioDataModel
import com.pacificbiosciences.pacbiocollectionmetadata.{
  CollectionMetadata => XsdCollectionMetadata
}

import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

// TODO(smcclellan): Add scaladoc, unittests

case class ParseResults(run: Run, collections: Seq[CollectionMetadata])

trait DataModelParser {
  def apply(dataModel: String): ParseResults
}

trait DataModelParserProvider {
  val dataModelParser: Singleton[DataModelParser]
}

object DataModelParserImpl extends DataModelParser {
  import SupportedAcquisitionStates._

  // this is copied from the chemistry bundle, specifically in the file
  // definitions/PacBioAutomationConstraints.xml
  val STANDARD_MOVIE_LENGTH_MAX = 600 // TODO maybe don't hard-code this?
  val CELL_TYPE_STANDARD = Some("Standard")
  val CELL_TYPE_LR = Some("LR")
  val FAILED_STATES = Set(FAILED, ABORTED, ERROR) // TODO(smcclellan): Include TRANSFER_FAILED?

  override def apply(dataModel: String): ParseResults =
    try {
      val xmlContentBytes: ByteArrayInputStream = new ByteArrayInputStream(
        dataModel.getBytes)

      // TODO(smcclellan): Validate against raw XSD (see https://jira.pacificbiosciences.com/browse/SE-17)
      // Currently, raw XSD validation rejects UniqueIds that begin with a number
//    val schemaFile = getClass.getResource("/pb-common-xsds/PacBioDataModel.xsd")
//    val schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaFile)
//    val validator = schema.newValidator()
//    validator.validate(new StreamSource(xmlContentBytes))

      val context: JAXBContext =
        JAXBContext.newInstance(new PacBioDataModel().getClass)
      val unmarshaller: Unmarshaller = context.createUnmarshaller()
      val parsedModel = new PacBioDataModel().getClass
        .cast(unmarshaller.unmarshal(xmlContentBytes))

      val runModels = parsedModel.getExperimentContainer.getRuns.getRun

      require(runModels.size() == 1, "expected exactly one <Run> element.")

      val runModel = runModels.asScala.head

      // Validate the Run state to have a better error message. Does jaxb silently fail for invalid enum values?
      // Doing this here to catch the error earlier, otherwise it will fail at the db level
      require(runModel.getStatus != null, "Invalid Run Status")

      val events = Option(runModel.getRecordedEvents)
        .flatMap(e => Option(e.getRecordedEvent))
        .map(asScalaBuffer)
        .getOrElse(Nil)

      def eventTimeByName(es: Seq[RecordedEventType],
                          name: String): Option[JodaDateTime] =
        es.find(_.getName == name)
          .flatMap(e => Option(e.getCreatedAt))
          .map(toDateTime)

      val completedAt = eventTimeByName(events, "RunCompletion")

      val transfersCompletedAt =
        eventTimeByName(events, "RunTransfersCompletion")

      val subreadModels =
        runModel.getOutputs.getSubreadSets.getSubreadSet.asScala.toSeq

      require(subreadModels.nonEmpty,
              "expected at least one <SubreadSet> element.")

      val collections: Seq[CollectionMetadata] = subreadModels.map { s =>
        require(s.getUniqueId != null,
                "expected UniqueId attribute in <SubreadSet> element.")

        val collectionMetadataModels =
          s.getDataSetMetadata.getCollections.getCollectionMetadata

        require(
          collectionMetadataModels.size() == 1,
          "expected exactly one <CollectionMetadata> element per <SubreadSet> element.")

        val collectionMetadataModel = collectionMetadataModels.asScala.head

        require(collectionMetadataModel.getWellSample != null,
                "expected a <WellSample> element.")

        //FIXME(mpkocher)(8-27-2107) This new validation is cause test failures, perhaps due to an invalid XML file.
        // If these states are invalid, this will generate an NPE at the DB level
//        require(
//          collectionMetadataModel.getStatus != null,
//          s"Collection ${collectionMetadataModel.getUniqueId} invalid state.")

        val movieMinutes =
          collectionMetadataModel.getAutomation.getAutomationParameters.getAutomationParameter.asScala
            .find(_.getName == "MovieLength")
            .get
            .getSimpleValue
            .toDouble

        val collectionPathUri: Option[Path] = for {
          pr <- Option(collectionMetadataModel.getPrimary)
          oo <- Option(pr.getOutputOptions)
          ur <- Option(oo.getCollectionPathUri)
          pa <- Option(Paths.get(ur))
        } yield pa

        val acqEvents = events.filter(_.getContext == s.getUniqueId)

        val acqStartedAt =
          eventTimeByName(acqEvents, "AcquisitionInitializeInfo")

        val acqCompletedAt =
          eventTimeByName(acqEvents, "AcquisitionCompletion")

        val cellType = if (movieMinutes <= STANDARD_MOVIE_LENGTH_MAX) {
          CELL_TYPE_STANDARD
        } else {
          CELL_TYPE_LR
        }

        CollectionMetadata(
          UUID.fromString(runModel.getUniqueId),
          UUID.fromString(s.getUniqueId),
          collectionMetadataModel.getWellSample.getWellName,
          collectionMetadataModel.getWellSample.getName,
          Option(collectionMetadataModel.getDescription),
          Option(collectionMetadataModel.getContext),
          collectionPathUri,
          collectionMetadataModel.getStatus,
          Option(collectionMetadataModel.getInstrumentId),
          Option(collectionMetadataModel.getInstrumentName),
          movieMinutes,
          Option(collectionMetadataModel.getRunDetails.getCreatedBy),
          acqStartedAt,
          acqCompletedAt,
          terminationInfo = None,
          cellType = cellType
        ) // TODO(smcclellan): Populate terminationInfo field when upstream data is available
      }

      val numStandardCells =
        collections.count(_.cellType == CELL_TYPE_STANDARD)
      val numLRCells =
        collections.count(_.cellType == CELL_TYPE_LR)

      // There are some values we need from the model that are the same across collections, but for some reason
      // not stored at the run level.
      val arbitraryCollectionMetadata =
        subreadModels.head.getDataSetMetadata.getCollections.getCollectionMetadata.asScala.head

      def getComponentVersion(md: XsdCollectionMetadata,
                              componentId: String): Option[String] = {
        Option(md.getComponentVersions).flatMap { versions =>
          versions.getValue.getVersionInfo.asScala
            .find(_.getName == componentId)
            .map(_.getVersion)
        }
      }

      val multiJobId: Option[Int] = Option(runModel.getOutputs)
        .flatMap(f => Option(f.getMultiJobId).map(_.toInt))

      val run = Run(
        dataModel,
        UUID.fromString(runModel.getUniqueId),
        runModel.getName,
        Option(runModel.getDescription),
        Option(runModel.getCreatedBy),
        Option(runModel.getCreatedAt).map(toDateTime),
        Option(runModel.getWhenStarted).map(toDateTime),
        transfersCompletedAt,
        completedAt,
        runModel.getStatus,
        Option(runModel.getChipType),
        collections.size,
        collections.count(c =>
          c.status == SupportedAcquisitionStates.COMPLETE),
        collections.count(c => FAILED_STATES.contains(c.status)),
        Option(arbitraryCollectionMetadata.getInstrumentName),
        Option(arbitraryCollectionMetadata.getInstrumentId),
        Option(arbitraryCollectionMetadata.getInstCtrlVer),
        Option(arbitraryCollectionMetadata.getSigProcVer),
        getComponentVersion(arbitraryCollectionMetadata, "chemistry"),
        Option(runModel.getTimeStampedName),
        terminationInfo = None, // TODO(smcclellan): Populate terminationInfo field when upstream data is available
        reserved = false,
        numStandardCells = numStandardCells,
        numLRCells = numLRCells,
        multiJobId = multiJobId
      )

      ParseResults(run, collections)

    } catch {
      case NonFatal(e) =>
        throw new UnprocessableEntityError(
          s"Data model parsing failed: ${e.toString}",
          e)
    }

  def toDateTime(c: XMLGregorianCalendar): JodaDateTime =
    new JodaDateTime(c.toGregorianCalendar.getTimeInMillis,
                     PacBioDateTimeFormat.TIME_ZONE)
}

trait DataModelParserImplProvider extends DataModelParserProvider {
  override val dataModelParser: Singleton[DataModelParser] = Singleton(
    DataModelParserImpl)
}
