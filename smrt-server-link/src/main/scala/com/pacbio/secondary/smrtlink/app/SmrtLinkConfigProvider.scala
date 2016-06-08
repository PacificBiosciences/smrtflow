package com.pacbio.secondary.smrtlink.app

import java.nio.file.Paths

import com.pacbio.common.database.DatabaseProvider
import com.pacbio.common.dependency.{Singleton, TypesafeSingletonReader}
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.{JobResourceResolver, PacBioIntJobResolver}
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, CommandTemplate}

trait SmrtLinkConfigProvider {
  this: DatabaseProvider with PbsmrtpipeConfigLoader with EngineCoreConfigLoader =>

  val configReader = TypesafeSingletonReader.fromConfig().in("pb-services")

  override val dbURI: Singleton[String] = configReader.getString("db-uri").required
  val port: Singleton[Int] = configReader.getInt("port").orElse(8070)
  val host: Singleton[String] = configReader.getString("host").orElse("0.0.0.0")

  val jobEngineConfig: Singleton[EngineConfig] = Singleton(() => engineConfig)
  val cmdTemplate: Singleton[Option[CommandTemplate]] = Singleton(() => loadCmdTemplate)
  val pbsmrtpipeEngineOptions: Singleton[PbsmrtpipeEngineOptions] =
    Singleton(() => loadPbsmrtpipeEngineConfigOrDefaults)
  val jobResolver: Singleton[JobResourceResolver] =
    Singleton(() => new PacBioIntJobResolver(Paths.get(jobEngineConfig().pbRootJobDir)))
}