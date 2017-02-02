package com.pacbio.secondary.smrtlink.database

import java.sql.{Connection, SQLException}
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGPoolingDataSource
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Need to be able to instantiate this from application.conf
case class DatabaseConfig(dbName: String,
                          username: String,
                          password: String,
                          server: String = "localhost",
                          port: Int = 5432,
                          maxConnections: Int = 10) {

  val jdbcURI = s"jdbc:postgresql://$server:$port/$dbName?user=$username&password=$password"

  /**
    * Util to create a new PG datasource instance. The caller should explicitly call .close() when the
    * datasource is no longer needed.
    * @return
    */
  def toDataSource: PGPoolingDataSource = {
    // Who should be responsible for calling .close() on the datasource ?
    val source = new PGPoolingDataSource()

    source.setDataSourceName(s"smrtlink-db-${UUID.randomUUID()}")
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

  def toDatabase = Database.forURL(jdbcURI, driver = "org.postgresql.Driver")
}

trait DatabaseUtils extends LazyLogging{

  object Migrator {
    /**
      * Run the Flyway Migrations and return the number of successfully applied
      * migrations
      *
      * @param dataSource Postgres Datasource
      * @return
      */
    def apply(dataSource: PGPoolingDataSource): Int = {
      val flyway = new Flyway()

      flyway.setBaselineOnMigrate(true)
      // The baseline version must be set to 0, if the V{X}_*.scala files start with 1
      flyway.setBaselineVersionAsString("0")
      flyway.setBaselineDescription("Initial Migration")
      flyway.setDataSource(dataSource)

      // does this close the connection to datasource if an exception is raised?
      println(s"Attempting to apply db migrations to $flyway with datasource ${dataSource.getUrl}")
      val numMigrations = flyway.migrate()
      println(s"Successfully applied $numMigrations db migration(s)")
      numMigrations
    }
  }

  object TestConnection {
    /**
      * Test Connection to Postgres Database
      *
      * @param source Postgres DataSource
      * @return
      */
    def apply(source: PGPoolingDataSource): String = {
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

  /**
    * Create Core SMRT Link tables defined in TableModels
    *
    * @return
    */
  def createTables(db: Database): Future[String] = {
    db.run(TableModels.schema.create).map(_ => "Successfully Created SMRT Link Tables")
  }

  /**
    * Drop the Core SMRT Link Tables defined in TableModels
    *
    * @return
    */
  def dropTables(db: Database): Future[String] = {
    db.run(TableModels.schema.drop).map(_ => "Successfully Dropped SMRT Link Tables")
  }

  /**
    * Drop the flyway "schema_version" table
    *
    * Note this configurable via flyway, but is hardcoded to the
    * default flyway value
    *
    * @return
    */
  def dropFlywayTable(db: Database): Future[String] = {
    val sx = sqlu"DROP TABLE schema_version"
    db.run(sx).map(_ => "Successfully Dropped Flyway schema_version table")
  }
}

object DatabaseUtils extends DatabaseUtils
