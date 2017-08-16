package com.pacbio.secondary.smrtlink.dependency

import java.io.{Reader, File}
import java.net.URL

import com.typesafe.config.{ConfigParseOptions, ConfigFactory, Config}

trait ConfigProvider {
  val parseOptions: Singleton[ConfigParseOptions] = Singleton(ConfigParseOptions.defaults())
  val config: Singleton[Config]
}

trait DefaultConfigProvider extends ConfigProvider {
  override val config: Singleton[Config] = Singleton(() => ConfigFactory.load(parseOptions()))
}

trait FileConfigProvider extends ConfigProvider {
  val configFile: Singleton[File]
  override val config: Singleton[Config] =
    Singleton(() => ConfigFactory.parseFile(configFile(), parseOptions()))
}

trait ReaderConfigProvider extends ConfigProvider {
  val configReader: Singleton[Reader]
  override val config: Singleton[Config] =
    Singleton(() => ConfigFactory.parseReader(configReader(), parseOptions()))
}

trait URLConfigProvider extends ConfigProvider {
  val configURL: Singleton[URL]
  override val config: Singleton[Config] =
    Singleton(() => ConfigFactory.parseURL(configURL(), parseOptions()))
}

trait StringConfigProvider extends ConfigProvider {
  val configString: Singleton[String]
  override val config: Singleton[Config] =
    Singleton(() => ConfigFactory.parseString(configString(), parseOptions()))
}