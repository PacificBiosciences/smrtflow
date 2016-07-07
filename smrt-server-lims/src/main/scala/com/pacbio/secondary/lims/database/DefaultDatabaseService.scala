package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.h2.H2DatabaseService

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
 * Default DatabaseService Impl
 *
 * This class is a wrapper of other DatabaseService implementations. It performs profiling and
 * logging of use independent of underlying RDMS.
 *
 * Production use is normally a single DB. The singleton here enforce that and removes the need to
 * synchronize and pass around a reference to a prod DB instance.
 *
 */
class DefaultDatabaseService extends DatabaseService {

  // timeout for the DB
  val timeout : Duration = 10 seconds

  // list of databases
  private val service = new H2DatabaseService() // primary DB backing "jdbc:h2:mem"

  /**
   * Inserts or updates the lims.yml content based on UUID
   *
   * @return
   */
  def setLimsYml(v: LimsYml) : String =  {
    service.setLimsYml(v)
  }

  def getByUUID(uuid: String) : String = {
    service.getByUUID(uuid)
  }

  def getByUUIDPrefix(uuid: String) : String = {
    service.getByUUIDPrefix(uuid)
  }
}
