package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.database.h2.H2Database
import com.pacbio.secondary.lims.services.{FileLookupSubreadsetUuid, LookupSubreadsetUuid}

/**
 * Default DatabaseService Impl
 *
 * Abstracts away the underlying database, including if it is JDBC-based or otherwise.
 */
trait DefaultDatabase extends H2Database {
  this: JdbcDatabase =>
}