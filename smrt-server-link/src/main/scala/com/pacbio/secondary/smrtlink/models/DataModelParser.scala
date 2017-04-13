package com.pacbio.secondary.smrtlink.models

import java.io.ByteArrayInputStream
import java.nio.file.{Paths, Path}
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.bind.{Unmarshaller, JAXBContext}
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.common.time.PacBioDateTimeFormat
import com.pacificbiosciences.pacbiobasedatamodel.SupportedAcquisitionStates
import com.pacificbiosciences.pacbiodatamodel.PacBioDataModel
import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.JavaConversions._
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

  val FAILED_STATES = Set(FAILED, ABORTED, ERROR) // TODO(smcclellan): Include TRANSFER_FAILED?

  override def apply(dataModel: String): ParseResults = try {
    val xmlContentBytes: ByteArrayInputStream = new ByteArrayInputStream(dataModel.getBytes)

    // TODO(smcclellan): Validate against raw XSD (see https://jira.pacificbiosciences.com/browse/SE-17)
    // Currently, raw XSD validation rejects UniqueIds that begin with a number
//    val schemaFile = getClass.getResource("/pb-common-xsds/PacBioDataModel.xsd")
//    val schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaFile)
//    val validator = schema.newValidator()
//    validator.validate(new StreamSource(xmlContentBytes))

    val context: JAXBContext = JAXBContext.newInstance(new PacBioDataModel().getClass)
    val unmarshaller: Unmarshaller = context.createUnmarshaller()
    val parsedModel =  new PacBioDataModel().getClass.cast(unmarshaller.unmarshal(xmlContentBytes))

    val runModels = parsedModel
      .getExperimentContainer
      .getRuns
      .getRun

    require(runModels.size() == 1, "expected exactly one <Run> element.")

    val runModel = runModels.head

    val events = Option(runModel.getRecordedEvents)
      .flatMap(e => Option(e.getRecordedEvent))
      .map(asScalaBuffer)
      .getOrElse(Nil)

    val completedAt = events
      .find(_.getName == "RunCompletion")
      .map(_.getCreatedAt)
      .map(toDateTime)
    val transfersCompletedAt = events
      .find(_.getName == "RunTransfersCompletion")
      .map(_.getCreatedAt)
      .map(toDateTime)

    val subreadModels = runModel
      .getOutputs
      .getSubreadSets
      .getSubreadSet
      .toSeq

    require(subreadModels.nonEmpty, "expected at least one <SubreadSet> element.")

    val collections: Seq[CollectionMetadata] = subreadModels.map { s =>
      require(s.getUniqueId != null, "expected UniqueId attribute in <SubreadSet> element.")

      val collectionMetadataModels = s
        .getDataSetMetadata
        .getCollections
        .getCollectionMetadata

      require(
        collectionMetadataModels.size() == 1,
        "expected exactly one <CollectionMetadata> element per <SubreadSet> element.")

      val collectionMetadataModel = collectionMetadataModels.head

      require(collectionMetadataModel.getWellSample != null, "expected a <WellSample> element.")

      val movieMinutes =
        collectionMetadataModel
          .getAutomation
          .getAutomationParameters
          .getAutomationParameter
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

      val acqStartedAt = events
        .filter(_.getContext == s.getUniqueId)
        .find(_.getName == "AcquisitionInitializeInfo")
        .map(_.getCreatedAt)
        .map(toDateTime)

      val acqCompletedAt = events
        .filter(_.getContext == s.getUniqueId)
        .find(_.getName == "AcquisitionCompletion")
        .map(_.getCreatedAt)
        .map(toDateTime)

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
        terminationInfo = None) // TODO(smcclellan): Populate terminationInfo field when upstream data is available
    }

    // There are some values we need from the model that are the same across collections, but for some reason
    // not stored at the run level.
    val arbitraryCollectionMetadata = subreadModels
      .head
      .getDataSetMetadata
      .getCollections
      .getCollectionMetadata
      .head

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
      collections.size,
      collections.count(c => c.status == SupportedAcquisitionStates.COMPLETE),
      collections.count(c => FAILED_STATES.contains(c.status)),
      Option(arbitraryCollectionMetadata.getInstrumentName),
      Option(arbitraryCollectionMetadata.getInstrumentId),
      Option(arbitraryCollectionMetadata.getInstCtrlVer),
      Option(arbitraryCollectionMetadata.getSigProcVer),
      Option(runModel.getTimeStampedName),
      terminationInfo = None, // TODO(smcclellan): Populate terminationInfo field when upstream data is available
      reserved = false
    )

    ParseResults(run, collections)

  } catch {
    case NonFatal(e) =>
      throw new UnprocessableEntityError(s"Data model parsing failed: ${e.toString}", e)
  }

  def toDateTime(c: XMLGregorianCalendar): JodaDateTime =
    new JodaDateTime(c.toGregorianCalendar.getTimeInMillis, PacBioDateTimeFormat.TIME_ZONE)
}

trait DataModelParserImplProvider extends DataModelParserProvider {
  override val dataModelParser: Singleton[DataModelParser] = Singleton(DataModelParserImpl)
}