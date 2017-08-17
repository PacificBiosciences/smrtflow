package com.pacbio.secondary.smrtlink.models

import java.net.URL
import java.nio.file.Path
import java.util.UUID

import spray.json._

/**
  * Created by mkocher on 1/4/17.
  */
object ConfigModels {

  // PacBio SMRT Link System Configuration IO. These must be consistent with the Avro Schema
  // See the SmrtLinkSystemConfig.avsc for model details and documentation.
  case class SmrtflowPacBioSystemConfig(tmpDir: Path,
                                        logDir: Path,
                                        pgDataDir: Path,
                                        tomcatPort: Int = 8080,
                                        tomcatMemory: Int = 1024,
                                        smrtViewPort: Int = 8084,
                                        smrtLinkServerMemoryMin: Int = 4096,
                                        smrtLinkServerMemoryMax: Int = 4096,
                                        remoteBundleUrl: Option[URL] = None,
                                        smrtLinkSystemRoot: Option[Path] = None,
                                        smrtLinkSystemId: Option[UUID] = None, // This needs to be made required after DEP changes
                                        mailPort: Option[Int] = Some(25),
                                        mailHost: Option[String] = None,
                                        mailUser: Option[String] = None,
                                        mailPassword: Option[String] = None)

  case class SmrtflowDbPropertiesConfig(databaseName: String,
                                  user: String,
                                  password: String,
                                  portNumber: Int,
                                  serverName: String)

  case class SmrtflowDbConfig(properties: SmrtflowDbPropertiesConfig)

  case class SmrtflowServerConfig(port: Int,
                                  manifestFile: Option[Path],
                                  eventUrl: Option[URL],
                                  dnsName: Option[String],
                                  bundleDir: Path)

  case class SmrtflowEngineConfig(maxWorkers: Int = 35,
                                  jobRootDir: Path,
                                  pbsmrtpipePresetXml: Option[Path])

  case class SmrtflowConfig(server: SmrtflowServerConfig,
                            engine: SmrtflowEngineConfig,
                            db: SmrtflowDbConfig)

  case class RootSmrtflowConfig(smrtflow: SmrtflowConfig,
                                pacBioSystem: SmrtflowPacBioSystemConfig,
                                comment: Option[String])

  case class Wso2Credentials(wso2User: String, wso2Password: String)
}
