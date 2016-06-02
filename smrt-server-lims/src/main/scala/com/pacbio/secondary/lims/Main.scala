package com.pacbio.secondary.lims

import com.pacbio.common.app.BaseServer
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.lims.services.LimsApi

/**
 * Entry point for the LIMS service
 *
 * If running this code independently of other projects, this is the entry point.
 *
 * ```
 * # run the HTTP server and services
 * sbt run
 * ```
 *
 * Production use of the greater SMRT web services bundles everything to run in one HTTP server. See
 * `com.pacbio.secondary.lims.LimsProviders` for a list of the services.
 */
object Main extends App
  with BaseServer
  with LimsApi {

  override val host = providers.serverHost()
  override val port = providers.serverPort()

  LoggerOptions.parseAddDebug(args)

  start
}
