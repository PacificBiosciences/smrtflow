package com.pacbio.secondary.lims

import akka.actor.Actor
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.utils.StatusGeneratorProvider
import com.pacbio.common.services.StatusServiceProvider
import com.pacbio.common.time.{ClockProvider, SystemClockProvider}
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
    with ResolveDataSet
    // rest are to add the status service
    with StatusServiceProvider
    with StatusGeneratorProvider
    with SystemClockProvider {
  // required for StatusGeneratorProvider
  lazy override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
  lazy override val baseServiceId: Singleton[String] = Singleton("smrt-server-lims")

  lazy val jdbcUrl : String = conf.getString("smrt-server-lims.jdbc-url") // required for JdbcDatabaseService

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(importLimsYmlRoutes ~ resolveRoutes ~ statusService().routes)
}
