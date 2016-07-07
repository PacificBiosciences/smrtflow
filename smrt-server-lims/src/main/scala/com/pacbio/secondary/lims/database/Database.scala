package com.pacbio.secondary.lims.database

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
  lazy val databaseService = new DefaultDatabaseService()
}
