package com.pacbio.secondary.smrtlink.jsonprotocols

import com.pacbio.secondary.smrtlink.models._
import fommil.sjs.FamilyFormats
import org.joda.time.{DateTime => JodaDateTime}
import shapeless.cachedImplicit
import spray.json._

// This common model should not be defined here
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}
import com.pacbio.common.models._
import com.pacbio.common.semver.SemVersion
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetJsonProtocols
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.DataStoreJobFile
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportJsonProtocol
import com.pacbio.secondary.smrtlink.time.PacBioDateTimeFormat
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.jobtypes.ImportDataSetJobOptions


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


trait ProjectEnumProtocols extends DefaultJsonProtocol {
  import scala.util.control.Exception._

  private def errorHandling[E]: Catch[E] =
    handling(classOf[IllegalArgumentException]) by { ex => deserializationError("Unknown project enum", ex) }

  implicit object ProjectStateFormat extends RootJsonFormat[ProjectState.ProjectState] {
    def write(s: ProjectState.ProjectState): JsValue = JsString(s.toString)
    def read(v: JsValue): ProjectState.ProjectState = v match {
      case JsString(s) => errorHandling { ProjectState.fromString(s) }
      case _ => deserializationError("Expected state as JsString")
    }
  }

  implicit object ProjectUserRoleFormat extends RootJsonFormat[ProjectUserRole.ProjectUserRole] {
    def write(r: ProjectUserRole.ProjectUserRole): JsValue = JsString(r.toString)
    def read(v: JsValue): ProjectUserRole.ProjectUserRole = v match {
      case JsString(s) => errorHandling { ProjectUserRole.fromString(s) }
      case _ => deserializationError("Expected role as JsString")
    }
  }

  implicit object ProjectRequestRoleFormat extends RootJsonFormat[ProjectRequestRole.ProjectRequestRole] {
    def write(r: ProjectRequestRole.ProjectRequestRole): JsValue = JsString(r.toString)
    def read(v: JsValue): ProjectRequestRole.ProjectRequestRole = v match {
      case JsString(s) => errorHandling { ProjectRequestRole.fromString(s) }
      case _ => deserializationError("Expected role as JsString")
    }
  }
}


trait BoundServiceEntryPointJsonProtocol extends DefaultJsonProtocol with FamilyFormats{
  implicit val idAbleFormat = IdAbleJsonProtocol.IdAbleFormat
  implicit val serviceBoundEntryPointFormat = jsonFormat3(BoundServiceEntryPoint)
}

trait PbSmrtPipeServiceOptionsProtocol extends DefaultJsonProtocol with PipelineTemplateOptionProtocol with BoundServiceEntryPointJsonProtocol{
  import JobModels._

  //implicit val idAbleFormat = IdAbleJsonProtocol.IdAbleFormat

  implicit object PbSmrtPipeServiceOptionsFormat extends RootJsonFormat[PbSmrtPipeServiceOptions] {
    def write(o: PbSmrtPipeServiceOptions): JsValue = JsObject(
      "name" -> o.name.toJson,
      "pipelineId" -> o.pipelineId.toJson,
      "entryPoints" -> o.entryPoints.toJson,
      "taskOptions" -> o.taskOptions.toJson,
      "workflowOptions" -> o.workflowOptions.toJson,
      "projectId" -> o.projectId.toJson
    )

    def read(v: JsValue): PbSmrtPipeServiceOptions = {
      val jsObj = v.asJsObject
      jsObj.getFields("name", "pipelineId", "entryPoints", "taskOptions", "workflowOptions") match {
        case Seq(JsString(name), JsString(pipelineId), JsArray(entryPoints),
                 JsArray(taskOptions), JsArray(workflowOptions)) =>
          val projectId = jsObj.getFields("projectId") match {
            case Seq(JsNumber(pid)) => pid.toInt
            case _ => JobConstants.GENERAL_PROJECT_ID
          }
          PbSmrtPipeServiceOptions(
            name,
            pipelineId,
            entryPoints.map(_.convertTo[BoundServiceEntryPoint]),
            taskOptions.map(_.convertTo[ServiceTaskOptionBase]),
            workflowOptions.map(_.convertTo[ServiceTaskOptionBase]),
            projectId)
        case x => deserializationError(s"Expected PbSmrtPipeServiceOptions, got $x")
      }
    }
  }
}

trait ReportViewRuleProtocol extends DefaultJsonProtocol {
  implicit object reportViewRuleFormat extends RootJsonFormat[ReportViewRule] {
    def write(r: ReportViewRule) = r.rules
    def read(value: JsValue) = {
      val rules = value.asJsObject
      rules.getFields("id") match {
        case Seq(JsString(id)) => ReportViewRule(id, rules)
        case x => deserializationError(s"Expected ReportViewRule, got $x")
      }
    }
  }
}


//FIXME(mpkocher)(8-22-2017) This is duplicated yet different
trait JodaDateTimeProtocol extends DefaultJsonProtocol with FamilyFormats {
  import PacBioDateTimeFormat.DATE_TIME_FORMAT

  implicit object JodaDateTimeFormat extends JsonFormat[JodaDateTime] {
    def write(obj: JodaDateTime): JsValue = JsString(obj.toString(DATE_TIME_FORMAT))


    def read(json: JsValue): JodaDateTime = json match {
      case JsString(x) => JodaDateTime.parse(x, DATE_TIME_FORMAT)
      case _ => deserializationError("Expected DateTime as JsString")
    }
  }
}

trait AlarmProtocols extends DefaultJsonProtocol with FamilyFormats {

  implicit object AlarmSeverityFormat extends JsonFormat[AlarmSeverity.AlarmSeverity] {
    def write(obj: AlarmSeverity.AlarmSeverity): JsValue = JsString(obj.toString)

    def read(json: JsValue): AlarmSeverity.AlarmSeverity = json match {
      case JsString(x) => AlarmSeverity.alarmSeverityByName(x)
      case _ => deserializationError("Expected AlarmSeverity type as JsString")
    }
  }
}


trait LogLevelProtocol extends DefaultJsonProtocol with FamilyFormats {

  implicit object LogLevelFormat extends JsonFormat[LogLevel.LogLevel] {
    def write(obj: LogLevel.LogLevel): JsValue = JsString(obj.toString)

    def read(json: JsValue): LogLevel.LogLevel = json match {
      case JsString(x) => LogLevel.logLevelByName(x.toLowerCase)
      case _ => deserializationError("Expected LogLevel type as JsString")
    }
  }
}

// Requires custom JSON serialization because of recursive structure
trait DirectoryResourceProtocol extends DefaultJsonProtocol {
  this: SmrtLinkJsonProtocols =>

  implicit object DirectoryResourceFormat extends RootJsonFormat[DirectoryResource] {
    def write(obj: DirectoryResource) = JsObject(
      "fullPath" -> JsString(obj.fullPath),
      "subDirectories" -> JsArray(obj.subDirectories.map(this.write):_*),
      "files" -> JsArray(obj.files.map(pbFileResourceFormat.write):_*)
    )

    def read(value: JsValue): DirectoryResource = {
      value.asJsObject.getFields("fullPath", "subDirectories", "files", "lazyLoaded") match {
        case Seq(JsString(fullPath), JsArray(subDirectories), JsArray(files)) =>
          DirectoryResource(fullPath, subDirectories.toSeq.map(this.read), files.toSeq.map(pbFileResourceFormat.read))
        case _ => deserializationError("Expected DirectoryResource fields: fullPath, subDirectories, files")
      }
    }
  }
}

trait SmrtLinkJsonProtocols
  extends UUIDJsonProtocol
  with JodaDateTimeProtocol
  with AlarmProtocols
  with DurationProtocol
  with DirectoryResourceProtocol
  with JobStatesJsonProtocol
  with PipelineTemplateOptionProtocol
  with SupportedRunStatesProtocols
  with SupportedAcquisitionStatesProtocols
  with PathProtocols
  with UrlProtocol
  with ProjectEnumProtocols
  with LogLevelProtocol
  with DataSetMetaTypesProtocol
  with PbSmrtPipeServiceOptionsProtocol
  with BoundServiceEntryPointJsonProtocol
  with ReportViewRuleProtocol
  with ReportJsonProtocol
  with DataSetJsonProtocols
  with FamilyFormats {

  implicit val pbSampleFormat = jsonFormat5(Sample)
  implicit val pbSampleCreateFormat = jsonFormat3(SampleCreate)
  implicit val pbSampleUpdateFormat = jsonFormat2(SampleUpdate)

  implicit val pbRunCreateFormat = jsonFormat1(RunCreate)
  implicit val pbRunUpdateFormat = jsonFormat2(RunUpdate)
  implicit val pbRunFormat = jsonFormat20(Run)
  implicit val pbRunSummaryFormat = jsonFormat19(RunSummary)
  implicit val pbCollectionMetadataFormat = jsonFormat15(CollectionMetadata)

  implicit val pbRegistryResourceFormat = jsonFormat6(RegistryResource)
  implicit val pbRegistryResourceCreateFormat = jsonFormat3(RegistryResourceCreate)
  implicit val pbRegistryResourceUpdateFormat = jsonFormat2(RegistryResourceUpdate)
  implicit val pbRegistryProxyRequestFormat = jsonFormat5(RegistryProxyRequest)


  // TODO(smcclellan): We should fix this by having pacbio-secondary import formats from base-smrt-server.
  // These should be acquired by mixing in SecondaryJobJsonProtocol, but we can't because of JodaDateTimeFormat collisions.
  implicit val engineJobFormat = SecondaryJobProtocols.EngineJobFormat
  implicit val engineConfigFormat = SecondaryJobProtocols.engineConfigFormat
  implicit val datastoreFileFormat = SecondaryJobProtocols.datastoreFileFormat
  implicit val datastoreFormat = SecondaryJobProtocols.datastoreFormat
  implicit val entryPointFormat = SecondaryJobProtocols.entryPointFormat
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
  implicit val dataStoreServiceFileFormat = jsonFormat13(DataStoreServiceFile)
  implicit val dataStoreReportFileFormat = jsonFormat2(DataStoreReportFile)

  // Old Job Options
  implicit val mergeDataSetServiceOptionFormat = jsonFormat3(DataSetMergeServiceOptions)
  implicit val deleteJobServiceOptions = jsonFormat4(DeleteJobServiceOptions)

  // New Job Options model
  implicit val importDataSetJobOptionJsonFormat = jsonFormat5(ImportDataSetJobOptions)

  implicit val projectFormat: RootJsonFormat[Project] = cachedImplicit
  implicit val fullProjectFormat: RootJsonFormat[FullProject] = cachedImplicit
  implicit val projectRequestFormat: RootJsonFormat[ProjectRequest] = cachedImplicit
  implicit val projectUserRequestFormat: RootJsonFormat[ProjectRequestUser] = cachedImplicit

  implicit val eulaFormat = jsonFormat6(EulaRecord)
  implicit val eulaAcceptanceFormat = jsonFormat2(EulaAcceptance)

  implicit val datasetUpdateFormat = jsonFormat1(DataSetUpdateRequest)
  implicit val datastoreUpdateFormat = jsonFormat3(DataStoreFileUpdateRequest)

  implicit val pacbioBundleVersionFormat = jsonFormat5(SemVersion.apply)
  // this model has a val assigned and requires a custom serialization
  implicit val pacbioBundleFormat = jsonFormat(PacBioDataBundle.apply, "typeId", "version", "importedAt", "createdBy", "isActive")
  implicit val pacbioBundleRecordFormat = jsonFormat1(PacBioBundleRecord)
  implicit val pacbioBundleUpgradeFormat = jsonFormat1(PacBioDataBundleUpgrade)

  implicit val smrtlinkEventMessageFormat = jsonFormat5(SmrtLinkEvent.apply)
  implicit val smrtlinkSystemEventMessageFormat = jsonFormat7(SmrtLinkSystemEvent.apply)

  implicit val externalServerStatusFormat = jsonFormat2(ExternalServerStatus.apply)

  implicit val techSupportSystemStatusRecordFormat = jsonFormat2(TechSupportSystemStatusRecord.apply)
  implicit val techSupportJobRecordFormat = jsonFormat3(TechSupportJobRecord.apply)

  implicit val clientLogMessageFormat = jsonFormat3(ClientLogMessage)

  // We bring the required imports from SecondaryJobJsonProtocols like this, as opposed to using it as a mixin, because
  // of namespace conflicts.
  implicit val pipelineTemplateFormat = SecondaryJobProtocols.PipelineTemplateFormat
  implicit val pipelineTemplateViewRule = SecondaryJobProtocols.pipelineTemplateViewRule
  implicit val importConvertFastaOptionsFormat = SecondaryJobProtocols.ConvertImportFastaOptionsFormat
  implicit val movieMetadataToHdfSubreadOptionsFormat = SecondaryJobProtocols.MovieMetadataToHdfSubreadOptionsFormat
  implicit val mergeDataSetOptionsFormat = SecondaryJobProtocols.MergeDataSetOptionsFormat
  implicit val importConvertFastaBarcodeOptionsFormat = SecondaryJobProtocols.ConvertImportFastaBarcodesOptionsFormat
  implicit val importDataSetOptionsFormat = SecondaryJobProtocols.ImportDataSetOptionsFormat

  // Jobs
  implicit val jobEventRecordFormat = jsonFormat2(JobEventRecord)

  implicit val exportOptions = jsonFormat3(DataSetExportServiceOptions)
  implicit val deleteDataSetsOptions = jsonFormat3(DataSetDeleteServiceOptions)

  implicit val pbThrowableResponseFormat = jsonFormat3(ThrowableResponse)
  implicit val pbComponentFormat = jsonFormat5(PacBioComponentManifest)
  implicit val pbServiceConfigFormat = jsonFormat2(ServerConfig)
  implicit val pbServiceComponentFormat = jsonFormat3(ServiceComponent)
  implicit val pbServiceStatusFormat = jsonFormat6(ServiceStatus)
  implicit val pbAlarmFormat = jsonFormat3(Alarm)
  implicit val pbAlarmStatusFormat = jsonFormat5(AlarmStatus)
  implicit val pbLogResourceRecordFormat = jsonFormat3(LogResourceRecord)
  implicit val pbLogResourceFormat = jsonFormat4(LogResource)
  implicit val pbLogMessageRecordFormat = jsonFormat3(LogMessageRecord)
  implicit val pbLogMessageFormat = jsonFormat5(LogMessage)
  implicit val pbUserRecordFormat = jsonFormat5(UserRecord)
  implicit val pbFileResourceFormat = jsonFormat5(FileResource)
  implicit val pbDiskSpaceResourceFormat = jsonFormat3(DiskSpaceResource)
  implicit val subSystemResourceFormat = jsonFormat8(SubsystemResource)
  implicit val subSystemResourceRecordFormat = jsonFormat5(SubsystemResourceRecord)
  implicit val subSystemConfigFormat = jsonFormat3(SubsystemConfig)
  implicit val pbMessageResponseFormat = jsonFormat1(MessageResponse)

  // this is here to break a tie between otherwise-ambiguous implicits;
  // see the spray-json-shapeless documentation
  implicit val llFormat = LogLevelFormat
}

object SmrtLinkJsonProtocols extends SmrtLinkJsonProtocols
