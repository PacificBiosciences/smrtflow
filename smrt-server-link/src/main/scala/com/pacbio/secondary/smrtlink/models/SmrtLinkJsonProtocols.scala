package com.pacbio.secondary.smrtlink.models

import java.nio.file.{Paths, Path}

import com.pacbio.common.models._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes._
import com.pacbio.secondary.analysis.jobs.JobModels.DataStoreJobFile
import com.pacbio.secondary.analysis.jobs.{JobStatesJsonProtocol, SecondaryJobProtocols}
import com.pacbio.secondary.analysis.jobtypes.MergeDataSetOptions
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedRunStates, SupportedAcquisitionStates}
import spray.json._
import fommil.sjs.FamilyFormats
import shapeless.cachedImplicit
import java.util.UUID


trait ServiceTaskOptionProtocols extends DefaultJsonProtocol {

  implicit object ServiceTaskOptionFormat extends RootJsonFormat[ServiceTaskOptionBase] {

    def write(p: ServiceTaskOptionBase): JsObject = {

      def toV(px: ServiceTaskOptionBase): JsValue = {
        px match {
          case ServiceTaskIntOption(_, v, _) => JsNumber(v)
          case ServiceTaskBooleanOption(_, v, _) => JsBoolean(v)
          case ServiceTaskStrOption(_, v, _) => JsString(v)
          case ServiceTaskDoubleOption(_, v, _) => JsNumber(v)
          case ServiceTaskFloatOption(_, v, _) => JsNumber(v)
        }
      }

      JsObject(
        "optionId" -> JsString(p.id),
        "value" -> toV(p),
        "optionTypeId" -> JsString(p.optionTypeId)
      )
    }

    def read(value: JsValue): ServiceTaskOptionBase = {
      value.asJsObject.getFields("optionId", "value", "optionTypeId") match {
        case Seq(JsString(id), JsNumber(value_), JsString(optionTypeId)) =>
          optionTypeId match {
            case "pbsmrtpipe.option_types.integer" =>  ServiceTaskIntOption(id, value_.toInt, optionTypeId)
            case "pbsmrtpipe.option_types.double" =>  ServiceTaskDoubleOption(id, value_.toDouble, optionTypeId)
            case "pbsmrtpipe.option_types.float" =>  ServiceTaskFloatOption(id, value_.toFloat, optionTypeId)
            case x => deserializationError(s"Unknown number type '$x'")
          }
        case Seq(JsString(id), JsBoolean(value_), JsString(optionTypeId)) => ServiceTaskBooleanOption(id, value_, optionTypeId)
        case Seq(JsString(id), JsString(value_), JsString(optionTypeId)) => ServiceTaskStrOption(id, value_, optionTypeId)
        case _ => deserializationError("Expected Task Option")
      }
    }
  }
}

trait SupportedRunStatesProtocols extends DefaultJsonProtocol {
  implicit object SupportedRunStatesFormat extends RootJsonFormat[SupportedRunStates] {
    def write(s: SupportedRunStates): JsValue = JsString(s.value())
    def read(v: JsValue): SupportedRunStates = v match {
      case JsString(s) => SupportedRunStates.fromValue(s)
      case _ => deserializationError("Expected SupportedRunStates as JsString")
    }
  }
}

trait SupportedAcquisitionStatesProtocols extends DefaultJsonProtocol {
  implicit object SupportedAcquisitionStatesFormat extends RootJsonFormat[SupportedAcquisitionStates] {
    def write(s: SupportedAcquisitionStates): JsValue = JsString(s.value())
    def read(v: JsValue): SupportedAcquisitionStates = v match {
      case JsString(s) => SupportedAcquisitionStates.fromValue(s)
      case _ => deserializationError("Expected SupportedAcquisitionStates as JsString")
    }
  }
}

trait PathProtocols extends DefaultJsonProtocol {
  implicit object PathFormat extends RootJsonFormat[Path] {
    def write(p: Path): JsValue = JsString(p.toString)
    def read(v: JsValue): Path = v match {
      case JsString(s) => Paths.get(s)
      case _ => deserializationError("Expected Path as JsString")
    }
  }
}

trait EntryPointProtocols extends DefaultJsonProtocol with UUIDJsonProtocol {
  implicit object EitherIntOrUUIDFormat extends RootJsonFormat[Either[Int,UUID]] {
    def write(id: Either[Int, UUID]): JsValue = id match {
      case Left(id) => JsNumber(id)
      case Right(uuid) => uuid.toJson
    }
    def read(v: JsValue): Either[Int, UUID] = v match {
      case JsNumber(x) => Left(x.toInt)
      case JsString(s) => Right(UUID.fromString(s))
      case _ => deserializationError("Expected datasetId as either JsString or JsNumber")
    }
  }
}

trait SmrtLinkJsonProtocols
  extends BaseJsonProtocol
  with JobStatesJsonProtocol
  with ServiceTaskOptionProtocols
  with SupportedRunStatesProtocols
  with SupportedAcquisitionStatesProtocols
  with PathProtocols
  with EntryPointProtocols
  with FamilyFormats {

  implicit val pbSampleFormat = jsonFormat5(Sample)
  implicit val pbSampleCreateFormat = jsonFormat3(SampleCreate)
  implicit val pbSampleUpdateFormat = jsonFormat2(SampleUpdate)

  implicit val pbRunCreateFormat = jsonFormat1(RunCreate)
  implicit val pbRunUpdateFormat = jsonFormat2(RunUpdate)
  implicit val pbRunFormat = jsonFormat20(Run)
  implicit val pbRunSummaryFormat = jsonFormat19(RunSummary)
  implicit val pbCollectionMetadataFormat = jsonFormat14(CollectionMetadata)

  implicit val pbRegistryResourceFormat = jsonFormat6(RegistryResource)
  implicit val pbRegistryResourceCreateFormat = jsonFormat3(RegistryResourceCreate)
  implicit val pbRegistryResourceUpdateFormat = jsonFormat2(RegistryResourceUpdate)
  implicit val pbRegistryProxyRequestFormat = jsonFormat5(RegistryProxyRequest)


  // TODO(smcclellan): We should fix this by having pacbio-secondary import formats from base-smrt-server.
  // These should be acquired by mixing in SecondaryJobJsonProtocol, but we can't because of UUIDFormat and
  // DateTimeFormat collisions.
  implicit val engineJobFormat = SecondaryJobProtocols.engineJobFormat
  implicit val engineConfigFormat = SecondaryJobProtocols.engineConfigFormat
  implicit val datastoreFileFormat = SecondaryJobProtocols.datastoreFileFormat
  implicit val datastoreFormat = SecondaryJobProtocols.datastoreFormat
  implicit val entryPointFormat = SecondaryJobProtocols.entryPointFormat
  implicit val importDataSetOptionsFormat = SecondaryJobProtocols.importDataSetOptionsFormat
  implicit val jobEventFormat = SecondaryJobProtocols.jobEventFormat
  implicit val simpleDevJobOptionsFormat  = SecondaryJobProtocols.simpleDevJobOptionsFormat


  implicit val jobTypeFormat = jsonFormat2(JobTypeEndPoint)

  // Jobs
  implicit val pbSimpleStatusFormat = jsonFormat3(SimpleStatus)
  implicit val engineJobEntryPointsFormat = jsonFormat3(EngineJobEntryPoint)

  // DataSet
  implicit val dataSetMetadataFormat = jsonFormat16(DataSetMetaDataSet)
  implicit val datasetTypeFormat = jsonFormat6(ServiceDataSetMetaType)
  implicit val subreadDataSetFormat: RootJsonFormat[SubreadServiceDataSet] = cachedImplicit
  implicit val hdfSubreadServiceDataSetFormat: RootJsonFormat[HdfSubreadServiceDataSet] = cachedImplicit
  implicit val alignmentDataSetFormat: RootJsonFormat[AlignmentServiceDataSet] = cachedImplicit
  implicit val referenceDataSetFormat: RootJsonFormat[ReferenceServiceDataSet] = cachedImplicit
  implicit val ccsreadDataSetFormat: RootJsonFormat[ConsensusReadServiceDataSet] = cachedImplicit
  implicit val barcodeDataSetFormat: RootJsonFormat[BarcodeServiceDataSet] = cachedImplicit
  implicit val contigServiceDataSetFormat: RootJsonFormat[ContigServiceDataSet] = cachedImplicit
  implicit val gmapReferenceDataSetFormat: RootJsonFormat[GmapReferenceServiceDataSet] = cachedImplicit
  implicit val consensusAlignmentDataSetFormat: RootJsonFormat[ConsensusAlignmentServiceDataSet] = cachedImplicit

  implicit val dataStoreJobFileFormat = jsonFormat2(DataStoreJobFile)
  implicit val dataStoreServiceFileFormat = jsonFormat12(DataStoreServiceFile)
  implicit val dataStoreReportFileFormat = jsonFormat2(DataStoreReportFile)

  implicit val serviceBoundEntryPointFormat = jsonFormat3(BoundServiceEntryPoint)

  implicit val resolvedPbSmrtPipeOptionsFormat = jsonFormat5(PbSmrtPipeServiceOptions)
  implicit val mergeDataSetServiceOptionFormat = jsonFormat3(DataSetMergeServiceOptions)
  implicit val mergeDataSetOptionFormat = jsonFormat3(MergeDataSetOptions)

  implicit val projectFormat: RootJsonFormat[Project] = cachedImplicit
  implicit val fullProjectFormat: RootJsonFormat[FullProject] = cachedImplicit
  implicit val projectRequestFormat: RootJsonFormat[ProjectRequest] = cachedImplicit
  implicit val projectUserRequestFormat: RootJsonFormat[ProjectRequestUser] = cachedImplicit
  implicit val projectUserResponseFormat: RootJsonFormat[ProjectUserResponse] = cachedImplicit
}

object SmrtLinkJsonProtocols extends SmrtLinkJsonProtocols
