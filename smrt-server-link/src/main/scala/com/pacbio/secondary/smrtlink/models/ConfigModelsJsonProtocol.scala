package com.pacbio.secondary.smrtlink.models

import com.pacbio.secondary.smrtlink.analysis.jobs.{PathProtocols, UrlProtocol}
import spray.json._
import ConfigModels._
import com.pacbio.common.models.UUIDJsonProtocol

/**
  * Created by mkocher on 1/4/17.
  *
  * There's an annoying bug in Avro that doesn't support union types in JSON as expected
  *
  * https://issues.apache.org/jira/browse/AVRO-1582
  *
  * http://stackoverflow.com/questions/27485580/how-to-fix-expected-start-union-got-value-number-int-when-converting-json-to-av
  *
  * From the commandline, you should be able to validate JSON with this avro-tools 1.8.1
  *
  * avro-tools fromjson --schema-file my-schema.avsc config.json
  *
  * The errors are reasonably good with regards to if the json is malformed, or if the schema is
  * malformed. If the data isn't consistent with the schema, then there will be a clear error
  * of a missing key or incompatible type.
  *
  * However, this is broken for any schema that defines a union type resulting in an error
  *
  * AvroTypeException: Expected start-union. Got VALUE_STRING
  *
  * Therefore, we have to handwrite the json serialization and make sure it's consistent
  * with the Schema that is defined.
  *
  */
trait ConfigModelsJsonProtocol extends DefaultJsonProtocol with PathProtocols with UrlProtocol with UUIDJsonProtocol{

  implicit val smrtflowPacBioSystemConfigFormat = jsonFormat11(SmrtflowPacBioSystemConfig)
  implicit val smrtflowDbPropertiesConfigFormat = jsonFormat5(SmrtflowDbPropertiesConfig)
  implicit val smrtflowDbConfigFormat = jsonFormat1(SmrtflowDbConfig)
  implicit val smrtflowServerConfigFormat = jsonFormat5(SmrtflowServerConfig)
  implicit val smrtflowEngineConfigFormat = jsonFormat3(SmrtflowEngineConfig)
  implicit val smrtflowConfigFormat = jsonFormat3(SmrtflowConfig)
  implicit val rootSmrtflowConfigFormat = jsonFormat3(RootSmrtflowConfig)
  implicit val wso2CredentialsFormat = jsonFormat2(Wso2Credentials)
}

object ConfigModelsJsonProtocol extends ConfigModelsJsonProtocol
