package com.pacbio.secondary.smrtlink.analysis.jobs

import java.net.{URI, URL}
import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetFilterProperty,
  DataSetMetaTypes
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.common.models.{
  JodaDateTimeProtocol,
  PathProtocols,
  URIJsonProtocol,
  UUIDJsonProtocol
}
import com.pacbio.secondary.smrtlink.models.EngineConfig
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
import shapeless.cachedImplicit

import scala.util.{Failure, Success, Try}

/**
  * Custom SecondaryJsonProtocols for spray.json
  *
  * Created by mkocher on 4/21/15.
  */
trait JobStatesJsonProtocol extends DefaultJsonProtocol {

  implicit object JobStatesJsonFormat
      extends JsonFormat[AnalysisJobStates.JobStates] {
    def write(obj: AnalysisJobStates.JobStates): JsValue =
      JsString(obj.toString)

    def read(value: JsValue): AnalysisJobStates.JobStates = value match {
      case JsString(x) =>
        AnalysisJobStates.toState(x.toUpperCase) match {
          case Some(state) => state
          case _ => deserializationError("Expected valid job state.")
        }
      case _ => deserializationError("Expected valid job state.")
    }
  }

}

trait DataSetMetaTypesProtocol extends DefaultJsonProtocol {

  implicit object DataSetMetaTypesFormat
      extends JsonFormat[DataSetMetaTypes.DataSetMetaType] {
    def write(obj: DataSetMetaTypes.DataSetMetaType): JsValue =
      JsString(DataSetMetaTypes.typeToIdString(obj))

    def read(value: JsValue): DataSetMetaTypes.DataSetMetaType = value match {
      case JsString(x) =>
        DataSetMetaTypes.toDataSetType(x) match {
          case Some(m) => m
          case _ =>
            deserializationError(s"Expected valid DataSetMetaType. Got $x")
        }
      case _ => deserializationError("Expected valid DataSetMetaType.")
    }
  }

}

trait PipelineTemplateOptionProtocol extends DefaultJsonProtocol {

  implicit object PipelineTemplateOptionFormat
      extends RootJsonFormat[PipelineBaseOption] {

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
        case PipelineChoiceDoubleOption(_, _, v, _, c) =>
          Some(c.map(JsNumber(_)))
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
      value.asJsObject.getFields("id",
                                 "name",
                                 "default",
                                 "description",
                                 "optionTypeId") match {
        case Seq(JsString(id),
                 JsString(name),
                 JsString(default),
                 JsString(description),
                 JsString(OptionTypes.STR.optionTypeId)) =>
          PipelineStrOption(id, name, default, description)
        case Seq(JsString(id),
                 JsString(name),
                 JsBoolean(default),
                 JsString(description),
                 JsString(OptionTypes.BOOL.optionTypeId)) =>
          PipelineBooleanOption(id, name, default, description)
        case Seq(JsString(id),
                 JsString(name),
                 JsNumber(default: BigDecimal),
                 JsString(description),
                 JsString(OptionTypes.INT.optionTypeId)) =>
          PipelineIntOption(id, name, default.toInt, description)
        case Seq(JsString(id),
                 JsString(name),
                 JsNumber(default: BigDecimal),
                 JsString(description),
                 JsString(OptionTypes.FLOAT.optionTypeId)) =>
          PipelineDoubleOption(id, name, default.toDouble, description)
        case Seq(JsString(id),
                 JsString(name),
                 JsString(default),
                 JsString(description),
                 JsString(OptionTypes.CHOICE.optionTypeId)) =>
          val choices = value.asJsObject.getFields("choices") match {
            case Seq(JsArray(jsChoices)) =>
              jsChoices.map(_.convertTo[String]).toList
            case x =>
              deserializationError(s"Expected list of choices for $id, got $x")
          }
          PipelineChoiceStrOption(id, name, default, description, choices)
        case Seq(JsString(id),
                 JsString(name),
                 JsNumber(default: BigDecimal),
                 JsString(description),
                 JsString(OptionTypes.CHOICE_INT.optionTypeId)) =>
          val choices = value.asJsObject.getFields("choices") match {
            case Seq(jsChoices) => jsChoices.convertTo[Seq[Int]]
            case x =>
              deserializationError(s"Expected list of choices for $id, got $x")
          }
          PipelineChoiceIntOption(id,
                                  name,
                                  default.toInt,
                                  description,
                                  choices)
        case Seq(JsString(id),
                 JsString(name),
                 JsNumber(default: BigDecimal),
                 JsString(description),
                 JsString(OptionTypes.CHOICE_FLOAT.optionTypeId)) =>
          val choices = value.asJsObject.getFields("choices") match {
            case Seq(jsChoices) => jsChoices.convertTo[Seq[Double]]
            case x =>
              deserializationError(s"Expected list of choices for $id, got $x")
          }
          PipelineChoiceDoubleOption(id,
                                     name,
                                     default.toDouble,
                                     description,
                                     choices)
        case x => deserializationError(s"Expected PipelineOption, got $x")
      }
    }
  }

  implicit object ServiceTaskOptionFormat
      extends RootJsonFormat[ServiceTaskOptionBase] {

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
        case _ =>
          value.asJsObject.getFields("optionId") match {
            case Seq(JsString(s)) => s
            case _ =>
              deserializationError(
                s"Can't find id or optionId field in $value")
          }
      }
      value.asJsObject.getFields("value", "optionTypeId") match {
        case Seq(JsNumber(value_), JsString(optionTypeId)) =>
          optionTypeId match {
            case INT.optionTypeId =>
              ServiceTaskIntOption(id, value_.toInt, optionTypeId)
            case FLOAT.optionTypeId =>
              ServiceTaskDoubleOption(id, value_.toDouble, optionTypeId)
            case CHOICE_INT.optionTypeId =>
              ServiceTaskIntOption(id, value_.toInt, optionTypeId)
            case CHOICE_FLOAT.optionTypeId =>
              ServiceTaskDoubleOption(id, value_.toDouble, optionTypeId)
            case x => deserializationError(s"Unknown number type '$x'")
          }
        case Seq(JsBoolean(value_), JsString(BOOL.optionTypeId)) =>
          ServiceTaskBooleanOption(id, value_, BOOL.optionTypeId)
        case Seq(JsString(value_), JsString(STR.optionTypeId)) =>
          ServiceTaskStrOption(id, value_, STR.optionTypeId)
        case Seq(JsString(value_), JsString(CHOICE.optionTypeId)) =>
          ServiceTaskStrOption(id, value_, CHOICE.optionTypeId)
        case x => deserializationError(s"Expected Task Option, got $x")
      }
    }
  }

  implicit object BaseOptionFormat extends RootJsonFormat[PacBioBaseOption] {
    def write(p: PacBioBaseOption): JsObject = {
      p match {
        case o: ServiceTaskOptionBase =>
          ServiceTaskOptionFormat.write(p.asInstanceOf[ServiceTaskOptionBase])
        case o: PipelineBaseOption =>
          PipelineTemplateOptionFormat.write(
            p.asInstanceOf[PipelineBaseOption])
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

trait PipelineTemplatePresetJsonProtocol
    extends DefaultJsonProtocol
    with PipelineTemplateOptionProtocol {

  implicit object PipelineTemplatePresetFormat
      extends RootJsonFormat[PipelineTemplatePreset] {
    // XXX note that title and name are not in the underlying data model in
    // Scala (although they are present in pbcommand)
    def write(p: PipelineTemplatePreset): JsObject = JsObject(
      "title" -> JsString(
        s"Options for Pipeline Preset Template ${p.presetId}"),
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
    private def convertServiceTaskOptions(
        jsOptions: JsValue): Seq[ServiceTaskOptionBase] = {
      jsOptions match {
        case JsArray(opts) => opts.map(_.convertTo[ServiceTaskOptionBase])
        case JsObject(opts) =>
          opts.map {
            case (optionId: String, jsValue: JsValue) =>
              jsValue match {
                case JsNumber(value) =>
                  if (value.isValidInt)
                    ServiceTaskIntOption(optionId, value.toInt)
                  else ServiceTaskDoubleOption(optionId, value.toDouble)
                case JsBoolean(value) =>
                  ServiceTaskBooleanOption(optionId, value)
                case JsString(value) => ServiceTaskStrOption(optionId, value)
                case x =>
                  deserializationError(s"Can't convert to option from $x")
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

trait JsonProjectSupport {
  def getProjectId(jsObj: JsObject): Int = jsObj.getFields("projectId") match {
    case Seq(JsNumber(projectId)) => projectId.toInt
    case _ => JobConstants.GENERAL_PROJECT_ID
  }
}

// Previous Release EngineJob Data Models. See comments below regarding the cachedImplicit usage
trait EngineJob510JsonSupport
    extends DefaultJsonProtocol
    with JodaDateTimeProtocol
    with UUIDJsonProtocol
    with JobStatesJsonProtocol {

  case class SmrtLink510EngineJob(id: Int,
                                  uuid: UUID,
                                  name: String,
                                  comment: String,
                                  createdAt: JodaDateTime,
                                  updatedAt: JodaDateTime,
                                  state: AnalysisJobStates.JobStates,
                                  jobTypeId: String,
                                  path: String,
                                  jsonSettings: String,
                                  createdBy: Option[String],
                                  createdByEmail: Option[String],
                                  smrtlinkVersion: Option[String],
                                  isActive: Boolean = true,
                                  errorMessage: Option[String] = None,
                                  projectId: Int =
                                    JobConstants.GENERAL_PROJECT_ID,
                                  isMultiJob: Boolean = false,
                                  workflow: String = "{}",
                                  parentMultiJobId: Option[Int] = None,
                                  importedAt: Option[JodaDateTime] = None) {
    def toEngineJob() =
      EngineJob(
        id,
        uuid,
        name,
        comment,
        createdAt,
        updatedAt,
        updatedAt,
        state,
        jobTypeId,
        path,
        jsonSettings,
        createdBy,
        createdByEmail,
        smrtlinkVersion,
        isActive,
        errorMessage,
        projectId,
        isMultiJob,
        workflow,
        parentMultiJobId,
        importedAt
      )
  }

  val smrtLink510engineJobJson = jsonFormat20(SmrtLink510EngineJob)
}

object EngineJob510JsonSupport extends EngineJob510JsonSupport

trait EngineJobNewestJsonSupport
    extends DefaultJsonProtocol
    with JodaDateTimeProtocol
    with UUIDJsonProtocol
    with JobStatesJsonProtocol {

  lazy val engineJobJsonNewestFormat: RootJsonFormat[EngineJob] =
    cachedImplicit
}

object EngineJobNewestJsonSupport extends EngineJobNewestJsonSupport

/**
  * Custom JSON parser to handle backward compatibility
  *
  * Note, only Official SL releases are supported. (e.g. 5.1.0)
  *
  * There's some oddness to avoid a StackOverflow error with using
  * shapeless' cachedImplicit generated from duplicated
  * RootJsonFormat[EngineJob] implicits (?)
  *
  */
trait EngineJobJsonSupport
    extends DefaultJsonProtocol
    with JodaDateTimeProtocol
    with UUIDJsonProtocol
    with JobStatesJsonProtocol {

  implicit object EngineJobJsonFormat extends RootJsonFormat[EngineJob] {

    override def read(json: JsValue): EngineJob =
      Try(EngineJobNewestJsonSupport.engineJobJsonNewestFormat.read(json)) match {
        case Success(engineJob) => engineJob
        case Failure(_) =>
          EngineJob510JsonSupport.smrtLink510engineJobJson
            .read(json)
            .toEngineJob()
      }

    override def write(obj: EngineJob): JsValue =
      EngineJobNewestJsonSupport.engineJobJsonNewestFormat.write(obj)

  }
}

object EngineJobJsonSupport extends EngineJobJsonSupport

case class ExampleServiceJobOption(name: String,
                                   private val projectId: Option[Int]) {
  val DEFAULT_PROJECT_ID = 1
  val projId: Int = projectId.getOrElse(DEFAULT_PROJECT_ID)
}

trait JobTypeSettingProtocol
    extends DefaultJsonProtocol
    with JodaDateTimeProtocol
    with UUIDJsonProtocol
    with JobStatesJsonProtocol
    with DataSetMetaTypesProtocol
    with PipelineTemplatePresetJsonProtocol
    with URIJsonProtocol
    with PathProtocols
    with EngineJobJsonSupport {

  import JobModels._

  //implicit val pacBioJobFormat = jsonFormat3(JobResource)
  implicit val datastoreFileFormat = jsonFormat10(DataStoreFile.apply)
  implicit val datastoreFormat = jsonFormat4(PacBioDataStore.apply)
  implicit val boundEntryPointFormat = jsonFormat2(BoundEntryPoint)
  implicit val jobEventFormat = jsonFormat6(JobEvent)
  implicit val entryPointFormat = jsonFormat3(EntryPoint)
  implicit val pipelineTemplateFormat = jsonFormat9(PipelineTemplate)

  // Job results
  implicit val jobResultSuccesFormat = jsonFormat6(ResultSuccess)
  implicit val jobResultFailureFormat = jsonFormat7(ResultFailed)
  implicit val jobResultFormat = jsonFormat2(JobCompletedResult)

  // Job Options
  implicit val directPbsmrtpipeJobOptionsFormat = jsonFormat5(
    PbsmrtpipeDirectJobOptions)

  // Engine Config
  implicit val engineConfigFormat = jsonFormat5(EngineConfig)

  // Pipeline DataStore Rules
  implicit val datastoreFileViewRules = jsonFormat5(DataStoreFileViewRule)
  implicit val pipelineDataStoreViewRules = jsonFormat3(
    PipelineDataStoreViewRules)

  implicit val tsSystemStatusManifest = jsonFormat9(
    TsSystemStatusManifest.apply)
  implicit val tsJobManifestFormat = jsonFormat11(TsJobManifest.apply)
  implicit val tsJobMetricHistoryFormat = jsonFormat10(TsJobMetricHistory)
  implicit val exportJobManifestFormat = jsonFormat4(ExportJobManifest)
}

trait SecondaryJobJsonProtocol extends JobTypeSettingProtocol

object SecondaryJobProtocols extends SecondaryJobJsonProtocol
