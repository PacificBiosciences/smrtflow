package com.pacbio.secondary.analysis.jobs

import java.net.URI
import java.util.UUID

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes._
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


// These are borrowed from base SMRT Server
trait UUIDJsonProtocol extends DefaultJsonProtocol {

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(obj: UUID): JsValue = JsString(obj.toString)

    def read(json: JsValue): UUID = json match {
      case JsString(x) => UUID.fromString(x)
      case _ => deserializationError("Expected UUID as JsString")
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

trait PipelineTemplateJsonSchemaUtils {

  private def toJ(opts: Seq[PipelineBaseOption]): JsObject = {
    val options = opts map {
      case p @ PipelineIntOption(id, name, value, description) => id -> JsObject("id" -> JsString(id), "title" -> JsString(name), "default" -> JsNumber(value), "description" -> JsString(description), "type" -> JsString("number"), "optionTypeId" -> JsString(p.pbOptionId))
      case p @ PipelineDoubleOption(id, name, value, description) => id -> JsObject("id" -> JsString(id), "title" -> JsString(name), "default" -> JsNumber(value), "description" -> JsString(description), "type" -> JsString("number"), "optionTypeId" -> JsString(p.pbOptionId))
      case p @ PipelineStrOption(id, name, value, description) => id -> JsObject("id" -> JsString(id), "title" -> JsString(name), "default" -> JsString(value), "description" -> JsString(description), "type" -> JsString("string"), "optionTypeId" -> JsString(p.pbOptionId))
      case p @ PipelineBooleanOption(id, name, value, description) => id -> JsObject("id" -> JsString(id), "title" -> JsString(name), "default" -> JsBoolean(value), "description" -> JsString(description), "type" -> JsString("boolean"), "optionTypeId" -> JsString(p.pbOptionId))
    }

    if (options.isEmpty) {
      JsObject()
    } else {
      val optionProperties = options.map { case (k, v) => k -> v }.toMap
      JsObject(optionProperties)
    }
  }

  def toSchema(options: Seq[PipelineBaseOption]): JsObject = {
    val requiredOptions = JsArray(options.map(x => JsString(x.id)).toVector)
    val optionProperties = toJ(options)
    if (options.isEmpty) {
      JsObject()
    } else {
      JsObject(
        "$schema" -> JsString("http://json-schema.org/draft-04/schema#"),
        "type" -> JsString("object"),
        "properties" -> optionProperties,
        "required" -> requiredOptions
      )
    }
  }
}

trait PipelineTemplateOptionProtocol extends DefaultJsonProtocol {

  implicit object PipelineTemplateOptionFormat extends RootJsonFormat[PipelineBaseOption] {

    def write(p: PipelineBaseOption): JsObject = {

      val x = p match {
        case PipelineBooleanOption(_, _, v, _) => JsBoolean(v)
        case PipelineStrOption(_, _, v, _) => JsString(v)
        case PipelineIntOption(_, _, v, _) => JsNumber(v)
        case PipelineDoubleOption(_, _, v, _) => JsNumber(v)
      }

      JsObject(
      "id" -> JsString(p.id),
      "name" -> JsString(p.name),
      "value" -> x,
      "description" -> JsString(p.description)
      )
    }

    def read(value: JsValue): PipelineBaseOption = {
      value.asJsObject.getFields("id", "name", "default", "description", "optionTypeId") match {
        case Seq(JsString(id), JsString(name), JsString(default), JsString(description), JsString("pbsmrtpipe.option_types.string")) =>
          PipelineStrOption(id, name, default, description)
        case Seq(JsString(id), JsString(name), JsBoolean(default), JsString(description), JsString("pbsmrtpipe.option_types.boolean")) =>
          PipelineBooleanOption(id, name, default, description)
        case Seq(JsString(id), JsString(name), JsNumber(default:BigDecimal), JsString(description), JsString("pbsmrtpipe.option_types.integer")) =>
          PipelineIntOption(id, name, default.toInt, description)
        case Seq(JsString(id), JsString(name), JsNumber(default:BigDecimal), JsString(description), JsString("pbsmrtpipe.option_types.float")) =>
          PipelineDoubleOption(id, name, default.toDouble, description)
        case _ => deserializationError("Expected PipelineOption")
      }
    }
  }
}


trait PipelineTemplateJsonProtocol extends DefaultJsonProtocol with PipelineTemplateJsonSchemaUtils with PipelineTemplateOptionProtocol{

  implicit object PipelineTemplateFormat extends RootJsonFormat[PipelineTemplate] {
    def write(p: PipelineTemplate): JsObject = {

      implicit val entryPointFormat = jsonFormat3(EntryPoint)

      val jobOptsSchema = toSchema(p.options)
      val taskOptsSchema = toSchema(p.taskOptions)
      val entryPoints = p.entryPoints.toJson
      val tags = p.tags.toJson

      // The task-options and options are returned here as a JSON schema formatted, not 'raw' pipeline template values
      JsObject(
        "id" -> JsString(p.id),
        "name" -> JsString(p.name),
        "description" -> JsString(p.description),
        "version" -> JsString(p.version),
        "entryPoints" -> entryPoints,
        "options" -> jobOptsSchema,
        "taskOptions" -> taskOptsSchema,
        "tags" -> tags
      )
    }

    // This is wrong. This is loading the JSONSchema TaskOption format
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


trait PipelineTemplatePresetJsonProtocol extends DefaultJsonProtocol with PipelineTemplateJsonSchemaUtils {

  implicit object PipelineTemplatePresetFormat extends RootJsonFormat[PipelineTemplatePreset] {
    def write(p: PipelineTemplatePreset): JsObject = {

      val jobOptsSchema = toSchema(p.options)
      val taskOptsScheam = toSchema(p.taskOptions)

      JsObject(
        "title" -> JsString(s"Options for Pipeline Preset Template ${p.presetId}"),
        "id" -> JsString(p.presetId),
        "templateId" -> JsString(p.templateId),
        "name" -> JsString(s"Pipeline Preset name ${p.presetId}"),
        "options" -> jobOptsSchema,
        "taskOptions" -> taskOptsScheam
      )
    }

    // This is wrong
    def read(value: JsValue) = {
      val nullEngineOptions = Seq[PipelineBaseOption]()
      val entryPoints = Seq[EntryPoint]()
      val tags = Seq("fake", "tags")
      value.asJsObject.getFields("id", "name") match {
        case Seq(JsString(id), JsString(name)) => PipelineTemplatePreset(id, name, nullEngineOptions, nullEngineOptions)
        case _ => deserializationError("Expected Pipeline template")
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



trait JobTypeSettingProtocol extends DefaultJsonProtocol
with JodaDateTimeProtocol
with UUIDJsonProtocol
with JobStatesJsonProtocol
with DataSetMetaTypesProtocol
with PipelineTemplateJsonProtocol
with PipelineTemplatePresetJsonProtocol with URIJsonProtocol {


  import JobModels._

  //implicit val pacBioJobFormat = jsonFormat3(JobResource)
  implicit val datastoreFileFormat = jsonFormat10(DataStoreFile)
  implicit val datastoreFormat = jsonFormat4(PacBioDataStore)
  implicit val boundEntryPointFormat = jsonFormat2(BoundEntryPoint)
  implicit val entryPointFormat = jsonFormat3(EntryPoint)
  implicit val jobEventFormat = jsonFormat5(JobEvent)

  // Job results
  implicit val jobResultSuccesFormat = jsonFormat6(ResultSuccess)
  implicit val jobResultFailureFormat = jsonFormat6(ResultFailed)
  implicit val jobResultFormat = jsonFormat2(JobCompletedResult)

  implicit val pipelineDoubleOptionFormat = jsonFormat4(PipelineDoubleOption)
  implicit val pipelineIntOptionFormat = jsonFormat4(PipelineIntOption)
  implicit val pipelineStrOptionFormat = jsonFormat4(PipelineStrOption)

  implicit val pipelineOptionViewRule = jsonFormat2(PipelineOptionViewRule)
  implicit val pipelineTemplateViewRule = jsonFormat4(PipelineTemplateViewRule)

  // Job Options
  implicit val directPbsmrtpipeJobOptionsFormat = jsonFormat4(PbsmrtpipeDirectJobOptions)
  implicit val simpleDevJobOptionsFormat = jsonFormat2(SimpleDevJobOptions)
  implicit val simpleDataTransferOptionsFormat = jsonFormat2(SimpleDataTransferOptions)
  implicit val movieMetadataToHdfSubreadOptionsFormat = jsonFormat2(MovieMetadataToHdfSubreadOptions)

  implicit val importDataSetOptionsFormat = jsonFormat2(ImportDataSetOptions)
  implicit val importConvertFastaOptionsFormat = jsonFormat4(ConvertImportFastaOptions)
  implicit val importDataStoreOptionsFormat = jsonFormat1(ImportDataStoreOptions)

  // Engine Config
  implicit val engineConfigFormat = jsonFormat4(EngineConfig)
  implicit val engineJobFormat = jsonFormat13(EngineJob)

  // Pipeline DataStore Rules
  implicit val datastoreFileViewRules = jsonFormat5(DataStoreFileViewRule)
  implicit val pipelineDataStoreViewRules = jsonFormat3(PipelineDataStoreViewRules)

}


trait SecondaryJobJsonProtocol extends JobTypeSettingProtocol

object SecondaryJobProtocols extends SecondaryJobJsonProtocol
