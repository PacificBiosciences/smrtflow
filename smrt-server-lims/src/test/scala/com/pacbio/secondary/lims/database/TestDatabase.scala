package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.database.h2.H2Database

/**
 * Test database for in-mem testing and parallel execution
 */
trait TestDatabase extends H2Database with JdbcDatabase {
  private val uuid = java.util.UUID.randomUUID()
  def jdbcUrl = "jdbc:h2:mem:" + uuid + ";DB_CLOSE_DELAY=3"
}
