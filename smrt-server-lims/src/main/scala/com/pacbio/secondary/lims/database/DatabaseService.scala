package com.pacbio.secondary.lims.database

import com.pacbio.secondary.lims.LimsYml


/**
 * All of the public DAO methods
 *
 * This trait is the main DB-agnostic interface that the rest of the codebase relies on. The Cake
 * patter is used to build up the desired DB implementation at runtime.
 *
 * ## Typical Production Use
 *
 * class MyClass extends DefaultDatabaseService {
 *   //.. do stuff that uses the db -- don't care about backend, use current
 *   getByUUID(...)
 *
 *
 * ## Production Example (Assuming H2 a JDBC-based RDBMS)
 *
 * class MyClass
 *     extends JdbcDatabaseService // specify JDBC URL
 *     extends H2DatabaseService { // use the H2 backend
 *   jdbcUrl = "jdbc:h2:./lims;DB_CLOSE_DELAY=3"
 *
 *   //.. do stuff that uses the db
 *   getByUUID(...)
 *
 *
 * ## Test Use
 *
 * class MySpec extends TestDatabaseService { // makes a new in-memory instance for just this test
 *   //.. do stuff that uses the db
 *   getByUUID(...)
 *
 *
 */
trait DatabaseService {

  def setLimsYml(v: LimsYml): String

  def getByUUID(uuid: String): String

  def getByUUIDPrefix(uuid: String): String
}





