package com.pacbio.secondary.lims.database

/**
 * Abstracts the JDBC URI so that it can be layered on or passed at runtime.
 */
trait JdbcDatabase {
  def jdbcUrl : String
}
