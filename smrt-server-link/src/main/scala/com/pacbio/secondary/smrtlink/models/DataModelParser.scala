package com.pacbio.secondary.smrtlink.models

import java.io.ByteArrayInputStream
import java.util.UUID
import javax.xml.bind.{Unmarshaller, JAXBContext}
import javax.xml.datatype.XMLGregorianCalendar

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

  override def apply(dataModel: String): ParseResults = {
    val xmlContentBytes: ByteArrayInputStream = new ByteArrayInputStream(dataModel.getBytes)
    val context: JAXBContext = JAXBContext.newInstance(new PacBioDataModel().getClass)
    val unmarshaller: Unmarshaller = context.createUnmarshaller()
    val parsedModel = try {
      new PacBioDataModel().getClass.cast(unmarshaller.unmarshal(xmlContentBytes))
    } catch {
      case e: Exception => throw new UnprocessableEntityError(s"XML did not conform to schema: ${e.toString}")
    }

    try {
      val runModels = parsedModel
        .getExperimentContainer
        .getRuns
        .getRun

      require(runModels.size() == 1, "expected exactly one <Run> element.")

      val runModel = runModels.head

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

        CollectionMetadata(
          UUID.fromString(runModel.getUniqueId),
          UUID.fromString(s.getUniqueId),
          collectionMetadataModel.getWellSample.getWellName,
          collectionMetadataModel.getWellSample.getName,
          Option(collectionMetadataModel.getDescription),
          Option(collectionMetadataModel.getContext),
          collectionMetadataModel.getStatus,
          Option(collectionMetadataModel.getInstrumentId),
          Option(collectionMetadataModel.getInstrumentName),
          movieMinutes,
          Option(collectionMetadataModel.getRunDetails.getWhenStarted).map(toDateTime),
          completedAt = None,     // TODO(smcclellan): Populate completedAt field when upstream data is available
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
        completedAt = None, // TODO(smcclellan): Populate completedAt field when upstream data is available
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
        throw new UnprocessableEntityError(s"Data model parsing failed: ${e.getMessage}", e)
    }
  }

  def toDateTime(c: XMLGregorianCalendar): JodaDateTime =
    new JodaDateTime(c.toGregorianCalendar.getTimeInMillis, PacBioDateTimeFormat.TIME_ZONE)
}

trait DataModelParserImplProvider extends DataModelParserProvider {
  override val dataModelParser: Singleton[DataModelParser] = Singleton(DataModelParserImpl)
}