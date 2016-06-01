package com.pacbio.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.core.spi.ContextAwareBase


class DefaultConfiguration extends LoggerConfig

class LoggerConfigurator extends ContextAwareBase with Configurator {
  def configure(lc: LoggerContext): Unit = {
    if (!LoggerOptions.configured) {
      val dc = new DefaultConfiguration()
      dc.configure(dc.logbackFile, dc.logFile, dc.debug, dc.logLevel)
    }
  }
}
