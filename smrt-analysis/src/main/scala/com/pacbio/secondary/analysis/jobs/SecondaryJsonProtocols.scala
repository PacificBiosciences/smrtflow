package com.pacbio.secondary.analysis.jobs

import java.net.{URI, URL}
import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes._
import com.pacbio.common.models.UUIDJsonProtocol
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

/**
 * Custom SecondaryJsonProtocols for spray.json
 *
 * Created by mkocher on 4/21/15.
 */

trait JobStatesJsonProtocol extends DefaultJsonProtocol {

  implicit object JobStatesJsonFormat extends JsonFormat[AnalysisJobStates.JobStates] {
    def write(obj: AnalysisJobStates.JobStates): JsValue = JsString(obj.toString)

    def read(value: JsValue): AnalysisJobStates.JobStates = value match {
      case JsString(x) => AnalysisJobStates.toState(x.toUpperCase) match {
        case Some(state) => state
        case _ => deserializationError("Expected valid job state.")
      }
      case _ => deserializationError("Expected valid job state.")
    }
  }

}

trait DataSetMetaTypesProtocol extends DefaultJsonProtocol {

  implicit object DataSetMetaTypesFormat extends JsonFormat[DataSetMetaTypes.DataSetMetaType] {
    def write(obj: DataSetMetaTypes.DataSetMetaType): JsValue = JsString(DataSetMetaTypes.typeToIdString(obj))

    def read(value: JsValue): DataSetMetaTypes.DataSetMetaType = value match {
      case JsString(x) => DataSetMetaTypes.toDataSetType(x) match {
        case Some(m) => m
        case _ => deserializationError(s"Expected valid DataSetMetaType. Got $x")
      }
      case _ => deserializationError("Expected valid DataSetMetaType.")
    }
  }

}


// These are borrowed from Base SMRT Server
trait JodaDateTimeProtocol extends DefaultJsonProtocol {

  implicit object JodaDateTimeFormat extends JsonFormat[JodaDateTime] {
    def write(obj: JodaDateTime): JsValue = JsString(obj.toString)

    def read(json: JsValue): JodaDateTime = json match {
      case JsString(x) => JodaDateTime.parse(x)
      case _ => deserializationError("Expected DateTime as JsString")
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

trait UrlProtocol extends DefaultJsonProtocol {
  implicit object UrlFormat extends RootJsonFormat[URL] {
    def write(u: URL): JsValue = JsString(u.toString)
    def read(v: JsValue): URL = v match {
      case JsString(sx) => new URL(sx) // Should this default to file:// if not provided?
      case _ => deserializationError("Expected URL as JsString")
    }
  }
}

// FIXME backwards compatibility for pbservice and older versions of smrtlink -
// this should be eliminated in favor of the automatic protocol if and when
// we can get away with it
trait EngineJobProtocol
    extends DefaultJsonProtocol
    with UUIDJsonProtocol
    with JodaDateTimeProtocol
    with JobStatesJsonProtocol {

  implicit object EngineJobFormat extends RootJsonFormat[EngineJob] {
    def write(obj: EngineJob): JsObject = {
      JsObject(
        "id" -> JsNumber(obj.id),
        "uuid" -> obj.uuid.toJson,
        "name" -> JsString(obj.name),
        "comment" -> JsString(obj.comment),
        "createdAt" -> obj.createdAt.toJson,
        "updatedAt" -> obj.updatedAt.toJson,
        "state" -> obj.state.toJson,
        "jobTypeId" -> JsString(obj.jobTypeId),
        "path" -> JsString(obj.path),
        "jsonSettings" -> JsString(obj.jsonSettings),
        "createdBy" -> obj.createdBy.toJson,
        "createdByEmail" -> obj.createdByEmail.toJson,
        "smrtlinkVersion" -> obj.smrtlinkVersion.toJson,
        "isActive" -> obj.isActive.toJson,
        "errorMessage" -> obj.errorMessage.toJson,
        "projectId" -> JsNumber(obj.projectId)
      )
    }

    def read(value: JsValue): EngineJob = {
      val jsObj = value.asJsObject
      jsObj.getFields("id", "uuid", "name", "comment", "createdAt", "updatedAt", "state", "jobTypeId", "path", "jsonSettings") match {
        case Seq(JsNumber(id), JsString(uuid), JsString(name), JsString(comment), JsString(createdAt), JsString(updatedAt), JsString(state), JsString(jobTypeId), JsString(path), JsString(jsonSettings)) =>

          def getBy(fieldName: String): Option[String] = {
            jsObj.getFields(fieldName) match {
              case Seq(JsString(aValue)) => Some(aValue)
              case _ => None
            }
          }
          val createdBy = getBy("createdBy")
          val createdByEmail = getBy("createdByEmail")
          val smrtlinkVersion = getBy("smrtlinkVersion")
          val errorMessage = getBy("errorMessage")
          val projectId = jsObj.getFields("projectId") match {
            case Seq(JsNumber(pid)) => pid.toInt
            case _ => JobConstants.GENERAL_PROJECT_ID
          }

          val isActive = jsObj.getFields("isActive") match {
            case Seq(JsBoolean(b)) => b
            case _ => true
          }

          EngineJob(id.toInt, UUID.fromString(uuid), name, comment,
                    JodaDateTime.parse(createdAt),
                    JodaDateTime.parse(updatedAt),
                    AnalysisJobStates.toState(state).get,
                    jobTypeId, path, jsonSettings,
                    createdBy, createdByEmail, smrtlinkVersion,
                    isActive, errorMessage, projectId)
        case x => deserializationError(s"Expected EngineJob, got $x")
      }
    }
  }
}

trait PipelineTemplateOptionProtocol extends DefaultJsonProtocol {

  implicit object PipelineTemplateOptionFormat extends RootJsonFormat[PipelineBaseOption] {

    def write(p: PipelineBaseOption): JsObject = {

      val default: JsValue = p match {
        case PipelineBooleanOption(_, _, v, _) => JsBoolean(v)
        case PipelineStrOption(_, _, v, _) => JsString(v)
        case PipelineIntOption(_, _, v, _) => JsNumber(v)
        case PipelineDoubleOption(_, _, v, _) => JsNumber(v)
        case PipelineChoiceStrOption(_, _, v, _, c) => JsString(v)
        case PipelineChoiceIntOption(_, _, v, _, c) => JsNumber(v)
        case PipelineChoiceDoubleOption(_, _, v, _, c) => JsNumber(v)
      }

      val choices: Option[Seq[JsValue]] = p match {
        case PipelineChoiceStrOption(_, _, v, _, c) => Some(c.map(JsString(_)))
        case PipelineChoiceIntOption(_, _, v, _, c) => Some(c.map(JsNumber(_)))
        case PipelineChoiceDoubleOption(_, _, v, _, c) => Some(c.map(JsNumber(_)))
        case _ => None
      }

      choices match {
        case Some(choices_) =>
          JsObject(
            "id" -> JsString(p.id),
            "name" -> JsString(p.name),
            "default" -> default,
            "description" -> JsString(p.description),
            "optionTypeId" -> JsString(p.optionTypeId),
            "choices" -> JsArray(choices_.toVector)
          )
        case None =>
          JsObject(
            "id" -> JsString(p.id),
            "name" -> JsString(p.name),
            "default" -> default,
            "description" -> JsString(p.description),
            "optionTypeId" -> JsString(p.optionTypeId)
          )
      }
    }

    def read(value: JsValue): PipelineBaseOption = {
      value.asJsObject.getFields("id", "name", "default", "description", "optionTypeId") match {
        case Seq(JsString(id), JsString(name), JsString(default), JsString(description), JsString(OptionTypes.STR.optionTypeId)) =>
          PipelineStrOption(id, name, default, description)
        case Seq(JsString(id), JsString(name), JsBoolean(default), JsString(description), JsString(OptionTypes.BOOL.optionTypeId)) =>
          PipelineBooleanOption(id, name, default, description)
        case Seq(JsString(id), JsString(name), JsNumber(default:BigDecimal), JsString(description), JsString(OptionTypes.INT.optionTypeId)) =>
          PipelineIntOption(id, name, default.toInt, description)
        case Seq(JsString(id), JsString(name), JsNumber(default:BigDecimal), JsString(description), JsString(OptionTypes.FLOAT.optionTypeId)) =>
          PipelineDoubleOption(id, name, default.toDouble, description)
        case Seq(JsString(id), JsString(name), JsString(default), JsString(description), JsString(OptionTypes.CHOICE.optionTypeId)) =>
          val choices = value.asJsObject.getFields("choices") match {
            case Seq(JsArray(jsChoices)) => jsChoices.map(_.convertTo[String]).toList
            case x => deserializationError(s"Expected list of choices for $id, got $x")
          }
          PipelineChoiceStrOption(id, name, default, description, choices)
        case Seq(JsString(id), JsString(name), JsNumber(default:BigDecimal), JsString(description), JsString(OptionTypes.CHOICE_INT.optionTypeId)) =>
          val choices = value.asJsObject.getFields("choices") match {
            case Seq(jsChoices) => jsChoices.convertTo[Seq[Int]]
            case x => deserializationError(s"Expected list of choices for $id, got $x")
          }
          PipelineChoiceIntOption(id, name, default.toInt, description, choices)
        case Seq(JsString(id), JsString(name), JsNumber(default:BigDecimal), JsString(description), JsString(OptionTypes.CHOICE_FLOAT.optionTypeId)) =>
          val choices = value.asJsObject.getFields("choices") match {
            case Seq(jsChoices) => jsChoices.convertTo[Seq[Double]]
            case x => deserializationError(s"Expected list of choices for $id, got $x")
          }
          PipelineChoiceDoubleOption(id, name, default.toDouble, description, choices)
        case x => deserializationError(s"Expected PipelineOption, got $x")
      }
    }
  }

  implicit object ServiceTaskOptionFormat extends RootJsonFormat[ServiceTaskOptionBase] {

    import OptionTypes._

    def write(p: ServiceTaskOptionBase): JsObject = {

      def toV(px: ServiceTaskOptionBase): JsValue = {
        px match {
          case ServiceTaskIntOption(_, v, _) => JsNumber(v)
          case ServiceTaskBooleanOption(_, v, _) => JsBoolean(v)
          case ServiceTaskStrOption(_, v, _) => JsString(v)
          case ServiceTaskDoubleOption(_, v, _) => JsNumber(v)
        }
      }

      JsObject(
        "id" -> JsString(p.id),
        "value" -> toV(p),
        "optionTypeId" -> JsString(p.optionTypeId)
      )
    }

    def read(value: JsValue): ServiceTaskOptionBase = {
      // FIXME see above
      val id = value.asJsObject.getFields("id") match {
        case Seq(JsString(s)) => s
        // FIXME the UI uses optionId but this is not what we use elsewhere!
        case _ => value.asJsObject.getFields("optionId") match {
          case Seq(JsString(s)) => s
          case _ => deserializationError(s"Can't find id or optionId field in $value")
        }
      }
      value.asJsObject.getFields("value", "optionTypeId") match {
        case Seq(JsNumber(value_), JsString(optionTypeId)) =>
          optionTypeId match {
            case INT.optionTypeId => ServiceTaskIntOption(id, value_.toInt, optionTypeId)
            case FLOAT.optionTypeId => ServiceTaskDoubleOption(id, value_.toDouble, optionTypeId)
            case CHOICE_INT.optionTypeId => ServiceTaskIntOption(id, value_.toInt, optionTypeId)
            case CHOICE_FLOAT.optionTypeId => ServiceTaskDoubleOption(id, value_.toDouble, optionTypeId)
            case x => deserializationError(s"Unknown number type '$x'")
          }
        case Seq(JsBoolean(value_), JsString(BOOL.optionTypeId)) => ServiceTaskBooleanOption(id, value_, BOOL.optionTypeId)
        case Seq(JsString(value_), JsString(STR.optionTypeId)) => ServiceTaskStrOption(id, value_, STR.optionTypeId)
        case Seq(JsString(value_), JsString(CHOICE.optionTypeId)) => ServiceTaskStrOption(id, value_, CHOICE.optionTypeId)
        case x => deserializationError(s"Expected Task Option, got $x")
      }
    }
  }

  implicit object BaseOptionFormat extends RootJsonFormat[PacBioBaseOption] {
    def write(p: PacBioBaseOption): JsObject = {
      p match {
        case o: ServiceTaskOptionBase => ServiceTaskOptionFormat.write(p.asInstanceOf[ServiceTaskOptionBase])
        case o: PipelineBaseOption => PipelineTemplateOptionFormat.write(p.asInstanceOf[PipelineBaseOption])
        case x => deserializationError(s"Expected some kind of Option, got $x")
      }
    }

    def read(value: JsValue): PacBioBaseOption = {
      value.asJsObject.getFields("name") match {
        case Seq(JsString(name)) => PipelineTemplateOptionFormat.read(value)
        case _ => ServiceTaskOptionFormat.read(value)
      }
    }
  }
}


trait PipelineTemplateJsonProtocol extends DefaultJsonProtocol with PipelineTemplateOptionProtocol {

  implicit object PipelineTemplateFormat extends RootJsonFormat[PipelineTemplate] {
    def write(p: PipelineTemplate): JsObject = {

      implicit val entryPointFormat = jsonFormat3(EntryPoint)

      val jobOpts = JsArray(p.options.map(_.toJson).toVector)
      val taskOpts = JsArray(p.taskOptions.map(_.toJson).toVector)
      val entryPoints = p.entryPoints.toJson
      val tags = p.tags.toJson

      JsObject(
        "id" -> JsString(p.id),
        "name" -> JsString(p.name),
        "description" -> JsString(p.description),
        "version" -> JsString(p.version),
        "entryPoints" -> entryPoints,
        "options" -> jobOpts,
        "taskOptions" -> taskOpts,
        "tags" -> tags
      )
    }

    def read(value: JsValue) = {
      implicit val entryPointFormat = jsonFormat3(EntryPoint)

      value.asJsObject.getFields("id", "name", "description", "version", "tags", "taskOptions", "entryPoints", "options") match {
        case Seq(JsString(id), JsString(name), JsString(description), JsString(version), JsArray(jtags), JsArray(jtaskOptions), JsArray(entryPoints), JsArray(joptions)) =>
          val tags = jtags.map(_.convertTo[String])
          val epoints = entryPoints.map(_.convertTo[EntryPoint])
          val taskOptions = jtaskOptions.map(_.convertTo[PipelineBaseOption])
          val engineOptions = joptions.map(_.convertTo[PipelineBaseOption])
          PipelineTemplate(id, name, description, version, engineOptions, taskOptions, epoints, tags, Seq[PipelineTemplatePreset]())
        case x => deserializationError(s"Expected Pipeline template Got $x")
      }
    }
  }
}


trait PipelineTemplatePresetJsonProtocol extends DefaultJsonProtocol with PipelineTemplateOptionProtocol {

  implicit object PipelineTemplatePresetFormat extends RootJsonFormat[PipelineTemplatePreset] {
    // XXX note that title and name are not in the underlying data model in
    // Scala (although they are present in pbcommand)
    def write(p: PipelineTemplatePreset): JsObject = JsObject(
        "title" -> JsString(s"Options for Pipeline Preset Template ${p.presetId}"),
        "presetId" -> JsString(p.presetId),
        "pipelineId" -> JsString(p.pipelineId),
        "name" -> JsString(s"Pipeline Preset name ${p.presetId}"),
        "options" -> JsArray(p.options.map(_.toJson).toVector),
        "taskOptions" -> JsArray(p.taskOptions.map(_.toJson).toVector)
      )

    /*
      Extract options from presets object.  This is slightly involved because
      we allow two different formats.  The preferred version looks exactly
      like the ServiceTaskOptionBase model:

        "options": [
            {
              "id": "pbsmrtpipe.options.max_nchunks",
              "value": 1,
              "optionTypeId": "integer"
            }
        ]

      But for the convenience of pbsmrtpipe users, we also permit this:

        "options": {
           "pbsmrtpipe.options.max_nchunks": 1
        }

      This isn't really an ideal solution, but it's much easier for humans to
      read and edit.  The preset.json that is eventually written and passed to
      pbsmrtpipe jobs will always use the full format, however.
    */
    private def convertServiceTaskOptions(jsOptions: JsValue): Seq[ServiceTaskOptionBase] = {
      jsOptions match {
        case JsArray(opts) => opts.map(_.convertTo[ServiceTaskOptionBase])
        case JsObject(opts) =>
          opts.map { case(optionId: String, jsValue: JsValue) =>
            jsValue match {
              case JsNumber(value) =>
                if (value.isValidInt) ServiceTaskIntOption(optionId, value.toInt)
                else ServiceTaskDoubleOption(optionId, value.toDouble)
              case JsBoolean(value) => ServiceTaskBooleanOption(optionId, value)
              case JsString(value) => ServiceTaskStrOption(optionId, value)
              case x => deserializationError(s"Can't convert to option from $x")
            }
          }.toList
        case x => deserializationError(s"Can't convert to options from $x")
      }
    }

    def read(value: JsValue) = {
      val nullEngineOptions = Seq[ServiceTaskOptionBase]()
      val entryPoints = Seq[EntryPoint]()
      val tags = Seq("fake", "tags")
      value.asJsObject.getFields("presetId", "pipelineId") match {
        case Seq(JsString(id), JsString(name)) =>
          val options = value.asJsObject.getFields("options") match {
            case Seq(opts: JsValue) => convertServiceTaskOptions(opts)
            case x => deserializationError(s"Can't convert to options from $x")
          }
          val taskOptions = value.asJsObject.getFields("taskOptions") match {
            case Seq(opts: JsValue) => convertServiceTaskOptions(opts)
            case x => deserializationError(s"Can't convert to options from $x")
          }
          PipelineTemplatePreset(id, name, options, taskOptions)
        case _ => deserializationError("Expected Pipeline template preset")
      }
    }
  }
}

trait URIJsonProtocol extends DefaultJsonProtocol {

  implicit object URIJsonProtocolFormat extends RootJsonFormat[URI] {
    def write(x: URI) = JsString(x.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(x) => new URI(x)
        case _ => deserializationError("Expected URI")
      }
    }
  }

}

trait JsonProjectSupport {
  def getProjectId(jsObj: JsObject): Int = jsObj.getFields("projectId") match {
    case Seq(JsNumber(projectId)) => projectId.toInt
    case _ => JobConstants.GENERAL_PROJECT_ID
  }
}

trait JobOptionsProtocols
    extends DefaultJsonProtocol
    with DataSetMetaTypesProtocol
    with JsonProjectSupport {

  implicit object ImportDataSetOptionsFormat extends RootJsonFormat[ImportDataSetOptions] {
    def write(o: ImportDataSetOptions) = JsObject(
      "path" -> o.path.toJson,
      "datasetType" -> o.datasetType.toJson,
      "projectId" -> o.projectId.toJson)
    def read(value: JsValue): ImportDataSetOptions = {
      val jsObj = value.asJsObject
      jsObj.getFields("path", "datasetType") match {
        case Seq(JsString(path), JsString(dsType)) =>
          ImportDataSetOptions(path, DataSetMetaTypes.toDataSetType(dsType).get,
                               getProjectId(jsObj))
        case x => deserializationError(s"Expected ImportDataSetOptions, got $x")
      }
    }
  }

  implicit object ConvertImportFastaOptionsFormat extends RootJsonFormat[ConvertImportFastaOptions] {
    def write(o: ConvertImportFastaOptions) = JsObject(
      "path" -> o.path.toJson,
      "name" -> o.name.toJson,
      "ploidy" -> o.ploidy.toJson,
      "organism" -> o.organism.toJson,
      "projectId" -> o.projectId.toJson)
    def read(value: JsValue): ConvertImportFastaOptions = {
      val jsObj = value.asJsObject
      jsObj.getFields("path", "name", "ploidy", "organism") match {
        case Seq(JsString(path), JsString(name), JsString(ploidy),
                 JsString(organism)) =>
          ConvertImportFastaOptions(path, name, ploidy, organism, getProjectId(jsObj))
        case x => deserializationError(s"Expected ConvertImportFastaOptions, got $x")
      }
    }
  }

  implicit object ConvertImportFastaBarcodesOptionsFormat extends RootJsonFormat[ConvertImportFastaBarcodesOptions] {
    def write(o: ConvertImportFastaBarcodesOptions) = JsObject(
      "path" -> o.path.toJson,
      "name" -> o.name.toJson,
      "projectId" -> o.projectId.toJson)
    def read(value: JsValue): ConvertImportFastaBarcodesOptions = {
      val jsObj = value.asJsObject
      jsObj.getFields("path", "name") match {
        case Seq(JsString(path), JsString(name)) =>
          ConvertImportFastaBarcodesOptions(path, name, getProjectId(jsObj))
        case x => deserializationError(s"Expected ConvertImportFastaBarcodesOptions, got $x")
      }
    }
  }

  implicit object MergeDataSetOptionsFormat extends RootJsonFormat[MergeDataSetOptions] {
    def write(o: MergeDataSetOptions) = JsObject(
      "datasetType" -> o.datasetType.toJson,
      "paths" -> o.paths.toJson,
      "name" -> o.name.toJson,
      "projectId" -> o.projectId.toJson)
    def read(value: JsValue): MergeDataSetOptions = {
      val jsObj = value.asJsObject
      jsObj.getFields("datasetType", "paths", "name") match {
        case Seq(JsString(datasetType), JsArray(paths), JsString(name)) =>
          MergeDataSetOptions(datasetType,
                              paths.map(_.convertTo[String]),
                              name,
                              getProjectId(jsObj))
        case x => deserializationError(s"Expected MergeDataSetOptions, got $x")
      }
    }
  }

  implicit object MovieMetadataToHdfSubreadOptionsFormat extends RootJsonFormat[MovieMetadataToHdfSubreadOptions] {
    def write(o: MovieMetadataToHdfSubreadOptions) = JsObject(
      "path" -> o.path.toJson,
      "name" -> o.name.toJson,
      "projectId" -> o.projectId.toJson)
    def read(value: JsValue): MovieMetadataToHdfSubreadOptions = {
      val jsObj = value.asJsObject
      jsObj.getFields("path", "name") match {
        case Seq(JsString(path), JsString(name)) =>
          MovieMetadataToHdfSubreadOptions(path, name, getProjectId(jsObj))
        case x => deserializationError(s"Expected MovieMetadataToHdfSubreadOptions, got $x")
      }
    }
  }

  implicit object ImportDataStoreOptionsFormat extends RootJsonFormat[ImportDataStoreOptions] {
    def write(o: ImportDataStoreOptions) = JsObject(
      "path" -> o.path.toJson,
      "projectId" -> o.projectId.toJson)
    def read(value: JsValue): ImportDataStoreOptions = {
      val jsObj = value.asJsObject
      jsObj.getFields("path") match {
        case Seq(JsString(path)) => ImportDataStoreOptions(path, getProjectId(jsObj))
        case x => deserializationError(s"Expected ImportDataStoreOptions, got $x")
      }
    }
  }

  implicit object SimpleDataTransferOptionsFormat extends RootJsonFormat[SimpleDataTransferOptions] {
    def write(o: SimpleDataTransferOptions) = JsObject(
      "src" -> o.src.toJson,
      "dest" -> o.dest.toJson,
      "projectId" -> o.projectId.toJson)
    def read(value: JsValue): SimpleDataTransferOptions = {
      val jsObj = value.asJsObject
      jsObj.getFields("src", "dest") match {
        case Seq(JsString(src), JsString(dest)) =>
          SimpleDataTransferOptions(src, dest, getProjectId(jsObj))
        case x => deserializationError(s"Expected SimpleDataTransferOptions, got $x")
      }
    }
  }
}


trait JobTypeSettingProtocol extends DefaultJsonProtocol
    with JodaDateTimeProtocol
    with UUIDJsonProtocol
    with JobStatesJsonProtocol
    with EngineJobProtocol
    with DataSetMetaTypesProtocol
    with PipelineTemplateJsonProtocol
    with PipelineTemplatePresetJsonProtocol
    with URIJsonProtocol
    with JobOptionsProtocols
    with PathProtocols {


  import JobModels._

  //implicit val pacBioJobFormat = jsonFormat3(JobResource)
  implicit val datastoreFileFormat = jsonFormat10(DataStoreFile)
  implicit val datastoreFormat = jsonFormat4(PacBioDataStore)
  implicit val boundEntryPointFormat = jsonFormat2(BoundEntryPoint)
  implicit val entryPointFormat = jsonFormat3(EntryPoint)
  implicit val jobEventFormat = jsonFormat6(JobEvent)

  // Job results
  implicit val jobResultSuccesFormat = jsonFormat6(ResultSuccess)
  implicit val jobResultFailureFormat = jsonFormat6(ResultFailed)
  implicit val jobResultFormat = jsonFormat2(JobCompletedResult)

  implicit val pipelineOptionViewRule = jsonFormat3(PipelineOptionViewRule)
  implicit val pipelineTemplateViewRule = jsonFormat4(PipelineTemplateViewRule)

  // Job Options
  implicit val directPbsmrtpipeJobOptionsFormat = jsonFormat5(PbsmrtpipeDirectJobOptions)
  implicit val simpleDevJobOptionsFormat = jsonFormat3(SimpleDevJobOptions)

  // Engine Config
  implicit val engineConfigFormat = jsonFormat4(EngineConfig)

  // Pipeline DataStore Rules
  implicit val datastoreFileViewRules = jsonFormat5(DataStoreFileViewRule)
  implicit val pipelineDataStoreViewRules = jsonFormat3(PipelineDataStoreViewRules)

  implicit val tsSystemStatusManifest = jsonFormat9(TsSystemStatusManifest.apply)
  implicit val tsJobManifestFormat = jsonFormat11(TsJobManifest.apply)

}


trait SecondaryJobJsonProtocol extends JobTypeSettingProtocol

object SecondaryJobProtocols extends SecondaryJobJsonProtocol
