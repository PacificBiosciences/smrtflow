package com.pacbio.secondary.lims

import akka.actor.Actor
import com.pacbio.common.dependency.TypesafeSingletonReader
import com.pacbio.secondary.lims.database.{DefaultDatabase, JdbcDatabase}
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSet}

/**
 * Parent Actor for all internal related web services work
 */
class InternalServiceActor extends Actor
    with JdbcDatabase
    with DefaultDatabase
    with ImportLimsYml
    with ResolveDataSet {

  lazy val config = TypesafeSingletonReader.fromConfig().in("smrt-server-lims")
  lazy val jdbcUrl : String = config.getString("jdbcUrl").required() // required for JdbcDatabaseService

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(importLimsYmlRoutes ~ resolveRoutes)
}
