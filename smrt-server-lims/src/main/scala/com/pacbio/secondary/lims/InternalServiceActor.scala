package com.pacbio.secondary.lims

import java.util.UUID

import akka.actor.Actor
import com.pacbio.common.models.Constants
import com.pacbio.common.services.utils.StatusGenerator
import com.pacbio.common.services.StatusService
import com.pacbio.common.time.SystemClock
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.lims.database.{DefaultDatabase, JdbcDatabase}
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSet}

/**
 * Parent Actor for all internal related web services work
 */
class InternalServiceActor extends Actor
    with ConfigLoader
    with JdbcDatabase
    with DefaultDatabase
    with ImportLimsYml
    with ResolveDataSet {

  lazy val jdbcUrl: String = conf.getString("smrt-server-lims.jdbc-url") // required for JdbcDatabaseService

  // needed as part of the smrtlink web services
  val statusService = new StatusService(new StatusGenerator(new SystemClock, "smrt-server-lims", UUID.randomUUID(), Constants.SMRTFLOW_VERSION))

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(importLimsYmlRoutes ~ resolveRoutes ~ statusService.routes)
}
