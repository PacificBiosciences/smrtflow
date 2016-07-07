package com.pacbio.secondary.lims.database

import java.util.UUID

import com.pacbio.secondary.lims.database.h2.H2DatabaseService

import scala.collection.mutable

/**
 * Singleton database instance for production use
 *
 * e.g.
 *
 * ```
 * val db = Database.databaseService
 * db.doSomething()
 * ```
 */
object Database {
  // singleton prod db service
  private lazy val databaseService = new DefaultDatabaseService()

  // flag and singleton for testing
  var testing: Boolean = false
  private var testDatabaseService: DatabaseService = null
  // required for tests
  def nextTest(uuid: String = UUID.randomUUID.toString, closeDelay: Int = 10): DatabaseService = {
    testing = true
    testDatabaseService = new H2DatabaseService("jdbc:h2:mem:" + uuid + ";DB_CLOSE_DELAY=" + closeDelay)
    testDatabaseService
  }

  /**
   * Returns the database service and supports thread-specific in-memory test DB
   *
   * @return
   */
  def get() : DatabaseService = {
    // if not testing, return the singleton
    if (!testing) {
      databaseService
    }
    else {
      testDatabaseService
    }
  }
}
