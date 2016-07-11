package com.pacbio.secondary.lims

import com.pacbio.secondary.lims.database.JdbcDatabaseService
import com.pacbio.secondary.lims.database.h2.H2DatabaseService

/**
 * Test database for in-mem testing and parallel execution
 */
trait TestDatabaseService extends H2DatabaseService with JdbcDatabaseService {
  private val uuid = java.util.UUID.randomUUID()
  def jdbcUrl = "jdbc:h2:mem:" + uuid + ";DB_CLOSE_DELAY=3"
}
