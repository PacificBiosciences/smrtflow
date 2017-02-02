package com.pacbio.secondary.smrtlink.models

import java.net.URL
import java.nio.file.Path

import spray.json._

/**
  * Created by mkocher on 1/4/17.
  */
object ConfigModels {

  // PacBio SMRT Link System Configuration IO. These must be consistent with the Avro Schema
  case class SmrtflowPacBioSystemConfig(tmpDir: Path,
                                        logDir: Path,
                                        tomcatPort: Int = 8080,
                                        tomcatMemory: Int = 1024,
                                        smrtViewPort: Int = 8084,
                                        smrtLinkServerMemoryMin: Int = 4096,
                                        smrtLinkServerMemoryMax: Int = 4096
                                       )

  case class SmrtflowDbPropertiesConfig(databaseName: String,
                                  user: String,
                                  password: String,
                                  portNumber: Int,
                                  serverName: String)

  case class SmrtflowDbConfig(properties: SmrtflowDbPropertiesConfig)

  case class SmrtflowServerConfig(port: Int,
                                  manifestFile: Option[Path],
                                  eventUrl: Option[URL],
                                  dnsName: Option[String])

  case class SmrtflowEngineConfig(maxWorkers: Int = 35,
                                  jobRootDir: Path,
                                  pbsmrtpipePresetXml: Option[Path])

  case class SmrtflowConfig(server: SmrtflowServerConfig,
                            engine: SmrtflowEngineConfig,
                            db: SmrtflowDbConfig)

  case class RootSmrtflowConfig(smrtflow: SmrtflowConfig,
                                pacBioSystem: SmrtflowPacBioSystemConfig,
                                comment: Option[String])


}
