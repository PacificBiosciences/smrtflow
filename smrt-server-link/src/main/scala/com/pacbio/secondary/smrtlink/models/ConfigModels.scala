package com.pacbio.secondary.smrtlink.models

import java.net.URL
import java.nio.file.Path
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions
import com.pacbio.secondary.smrtlink.database.DatabaseConfig
import spray.json._

/**
  * Created by mkocher on 1/4/17.
  */
object ConfigModels {

  val DEFAULT_MAX_GENERAL_WORKERS = 35
  val DEFAULT_MAX_QUICK_WORKERS = 10

  // PacBio SMRT Link System Configuration IO. These must be consistent with the Avro Schema
  // See the SmrtLinkSystemConfig.avsc for model details and documentation.
  case class SmrtflowPacBioSystemConfig(
      tmpDir: Path,
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
      mailPassword: Option[String] = None,
      enableCellReuse: Option[Boolean] = Some(false))

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

  case class SmrtflowEngineConfig(
      maxWorkers: Int = DEFAULT_MAX_GENERAL_WORKERS,
      jobRootDir: Path,
      pbsmrtpipePresetXml: Option[Path])

  case class SmrtflowConfig(server: SmrtflowServerConfig,
                            engine: SmrtflowEngineConfig,
                            db: SmrtflowDbConfig)

  case class RootSmrtflowConfig(smrtflow: SmrtflowConfig,
                                pacBioSystem: SmrtflowPacBioSystemConfig,
                                comment: Option[String])

  case class Wso2Credentials(wso2User: String, wso2Password: String)

  // Adding a new Job specific "system" config to be used in the new Job running layer
  // This should replace the EngineConfig layer
  case class SystemJobConfig(pbSmrtPipeEngineOptions: PbsmrtpipeEngineOptions,
                             host: String,
                             port: Int,
                             smrtLinkVersion: Option[String],
                             smrtLinkSystemId: UUID,
                             smrtLinkSystemRoot: Option[Path],
                             numGeneralWorkers: Int =
                               DEFAULT_MAX_GENERAL_WORKERS,
                             numQuickWorkers: Int = DEFAULT_MAX_QUICK_WORKERS,
                             externalEveUrl: Option[URL],
                             rootDbBackUp: Option[Path],
                             dbConfig: DatabaseConfig)
}
