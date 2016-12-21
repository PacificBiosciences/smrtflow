package com.pacbio.secondary.smrtlink.app

import java.nio.file.Paths

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.loaders.ManifestLoader
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.{JobResourceResolver, PacBioIntJobResolver}
import com.pacbio.secondary.analysis.pbsmrtpipe.{CommandTemplate, PbsmrtpipeEngineOptions}

trait SmrtLinkConfigProvider {
  this: PbsmrtpipeConfigLoader with EngineCoreConfigLoader =>

  val port: Singleton[Int] = Singleton(() => conf.getInt("smrtflow.services.port"))
  val host: Singleton[String] = Singleton(() => conf.getString("smrtflow.services.host"))

  val jobEngineConfig: Singleton[EngineConfig] = Singleton(() => engineConfig)
  val cmdTemplate: Singleton[Option[CommandTemplate]] = Singleton(() => loadCmdTemplate)
  val pbsmrtpipeEngineOptions: Singleton[PbsmrtpipeEngineOptions] =
    Singleton(() => loadPbsmrtpipeEngineConfigOrDefaults)
  val jobResolver: Singleton[JobResourceResolver] =
    Singleton(() => new PacBioIntJobResolver(jobEngineConfig().pbRootJobDir))

  // Unfortunately this is duplicated in the Manifest service
  val smrtLinkVersion: Singleton[Option[String]] =
    Singleton(() => ManifestLoader.loadFromConfig(conf).toList.find(_.id == ManifestLoader.SMRTLINK_ID).map(_.version))
  val smrtLinkToolsVersion: Singleton[Option[String]] =
    Singleton(() => ManifestLoader.loadFromConfig(conf).toList.find(_.id == ManifestLoader.SMRT_LINK_TOOLS_ID).map(_.version))
}