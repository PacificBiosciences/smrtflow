package com.pacbio.secondary.smrtlink.database

import java.sql.{Connection, SQLException}
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Need to be able to instantiate this from application.conf
case class SmrtLinkDatabaseConfig(dbName: String,
                                  username: String,
                                  password: String,
                                  server: String = "localhost",
                                  port: Int = 5432,
                                  maxConnections: Int = 20) {

  val jdbcURI =
    s"jdbc:postgresql://$server:$port/$dbName?user=$username&password=$password"

  /**
    * Util to create a new PG datasource instance. This should ONLY be used for migrations.
    *
    * @return
    */
  def toDataSource: PGSimpleDataSource = {
    //MK. It's not clear to me how this gets closed
    val source = new PGSimpleDataSource()

    // Localhost
    source.setServerName(server)
    source.setPortNumber(port)
    source.setDatabaseName(dbName)
    source.setUser(username)
    source.setPassword(password)
    source
  }

  def toDatabase = Database.forURL(jdbcURI, driver = "org.postgresql.Driver")
}

trait DatabaseUtils extends LazyLogging {

  object Migrator {

    /**
      * Run the Flyway Migrations and return the number of successfully applied
      * migrations
      *
      * @param dataSource Postgres Datasource
      * @return
      */
    def apply(dataSource: PGSimpleDataSource,
              target: MigrationVersion = MigrationVersion.LATEST): Int = {
      val flyway = new Flyway()

      flyway.setBaselineOnMigrate(true)
      // The baseline version must be set to 0, if the V{X}_*.scala files start with 1
      flyway.setBaselineVersionAsString("0")
      flyway.setBaselineDescription("Initial Migration")
      flyway.setDataSource(dataSource)
      flyway.setTarget(target)

      // does this close the connection to datasource if an exception is raised?
      println(
        s"Attempting to apply db migrations to $flyway with datasource ${dataSource.getUrl}")
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
    def apply(source: PGSimpleDataSource): String = {
      var conn: Connection = null
      try {
        conn = source.getConnection()
        s"Successfully connected to datasource ${source.getUrl}"
        // use connection
      } catch {
        case e: SQLException => {
          logger.error(
            s"Failed to connect to ${source.getUrl} Error code ${e.getErrorCode}\n ${e.getMessage}")
          throw e
        }
      } finally {
        if (conn != null) {
          try { conn.close(); } catch {
            case e: SQLException =>
              logger.error(
                s"Unable to close db connection to ${source.getUrl} ${e.getMessage}")
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
    db.run(TableModels.schema.create)
      .map(_ => "Successfully Created SMRT Link Tables")
  }

  /**
    * Drop the Core SMRT Link Tables defined in TableModels
    *
    * @return
    */
  def dropTables(db: Database): Future[String] = {
    db.run(TableModels.schema.drop)
      .map(_ => "Successfully Dropped SMRT Link Tables")
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
