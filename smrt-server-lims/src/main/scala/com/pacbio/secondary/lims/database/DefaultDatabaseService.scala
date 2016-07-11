package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.database.h2.H2DatabaseService

/**
 * Default DatabaseService Impl
 *
 * Abstracts away the underlying database, including if it is JDBC-based or otherwise.
 *
 */
trait DefaultDatabaseService extends H2DatabaseService {
  this: JdbcDatabaseService =>

}
