package com.pacbio.secondary.smrtlink.database

import java.sql.{Connection, SQLException}

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGPoolingDataSource
import slick.driver.PostgresDriver.api._


/**
  * Created by mkocher on 12/9/16.
  */
class Migrator(dbName: String, username: String, password: String, server: String = "localhost", port: Int = 5432, maxConnections: Int = 10) extends LazyLogging{

  private val dataSource: PGPoolingDataSource = {
    val source = new PGPoolingDataSource()

    source.setDataSourceName("SMRT Link Server Database")
    // Localhost
    source.setServerName(server)
    source.setPortNumber(port)
    source.setDatabaseName(dbName)
    source.setUser(username)
    source.setPassword(password)
    // Does this require to be 1 for the migrations to be applied?
    source.setMaxConnections(maxConnections)
    source
  }

  // Return the number of successfully applied migrations, or raises FlywayException
  def migrate(): Int = {

    val flyway = new Flyway()

    flyway.setBaselineOnMigrate(true)
    // The baseline version must be set to 0, if the V{X}_*.scala files start with 1
    flyway.setBaselineVersionAsString("0")
    flyway.setBaselineDescription("Initial Migration")
    flyway.setDataSource(dataSource)

    // does this close the connection to datasource if an exception is raised?
    println(s"Attempting to apply migrations to $flyway")
    val numMigrations = flyway.migrate()
    println(s"Applied $numMigrations")
    numMigrations
  }

  def testConnection(): String = {

    //FIXME(mpkocher)(2016-12-9) this should be refactoring into
    // a more idiomatic scala approach

    val source = dataSource
    var conn: Connection = null
    try {
      conn = source.getConnection()
      s"Successfully connected to datasource ${source.getUrl}"
      // use connection
    } catch  {
      case e: SQLException => {
        logger.error(s"Failed to connect to ${source.getUrl} Error code ${e.getErrorCode}\n ${e.getMessage}")
        throw e
      }
    } finally {
      if (conn != null) {
        try { conn.close(); } catch {
          case e: SQLException => logger.error(s"Unable to close db connection to ${source.getUrl} ${e.getMessage}")
        }
      }
    }

  }

}
