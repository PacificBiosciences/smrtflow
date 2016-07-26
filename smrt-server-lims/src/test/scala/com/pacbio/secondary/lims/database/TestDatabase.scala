package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.database.h2.H2Database

/**
 * Test database for in-mem testing and parallel execution
 *
 * Currently this uses an H2-backed in-memory database that allows for multiple connections and will
 * persist for 3 seconds after the last connection closes.
 */
trait TestDatabase extends H2Database with JdbcDatabase {
  // make the UUID once so that the test has a unique DB but multiple threads/connections can use it
  private val uuid = java.util.UUID.randomUUID()
  // recycle the same UUID as long as this instance is in use
  lazy val jdbcUrl = "jdbc:h2:mem:" + uuid + ";DB_CLOSE_DELAY=3"
}
