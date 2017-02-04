package com.pacbio.secondary.smrtserver.tools

import java.nio.file.Paths

import scala.collection.JavaConversions._
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.configloaders.EngineCoreConfigLoader
import com.pacbio.secondary.analysis.jobs.PacBioIntJobResolver
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.database.{DatabaseConfig, DatabaseUtils}
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import slick.driver.PostgresDriver.api._


case class DatabaseToolOptions(username: String,
                               password: String,
                               dbName: String,
                               server: String,
                               port: Int = 5432) extends LoggerConfig

object DatabaseTool extends CommandLineToolRunner[DatabaseToolOptions] with EngineCoreConfigLoader{

  import DatabaseUtils._

  val toolId = "pbscala.tools.database_tool"
  val VERSION = "0.2.0"
  val DESCRIPTION =
    """
      |Test connection and Run Database Migrations
    """.stripMargin

  // For the Postgres.app the defaults are
  // username is "postgres"
  // password is ""
  // server is "localhost"
  // database name is the username ($USER)
  // Need to think about how to connect this with the application.conf IO layer that
  // loads the db configuration
  val defaults = DatabaseToolOptions("smrtlink_user", "password", "smrtlink", "localhost")

  def toDefault(s: String) = s"(default: '$s')"

  val parser = new OptionParser[DatabaseToolOptions]("database-tools") {
    head("")
    note(DESCRIPTION)
    opt[String]('u', "user").action { (x, c) => c.copy(username = x)}.text(s"Database user name ${toDefault(defaults.username)}")
    opt[String]('p', "password").action {(x, c) => c.copy(password = x)}.text(s"Database Password ${toDefault(defaults.password)}")
    opt[String]('s', "server").action {(x, c) => c.copy(server = x)}.text(s"Database server ${toDefault(defaults.server)}")
    opt[String]('n', "db-name").action {(x, c) => c.copy(dbName = x)}.text(s"Database Name ${toDefault(defaults.dbName)}")
    opt[Int]("port").action {(x, c) => c.copy(port = x)}.text(s"Database port ${toDefault(defaults.port.toString)}")

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def run(c: DatabaseToolOptions): Either[ToolFailure, ToolSuccess] = {

    val dbConfig = DatabaseConfig(c.dbName, c.username, c.password, c.server, c.port)
    val startedAt = JodaDateTime.now()

    TestConnection(dbConfig.toDataSource)

    println(s"Attempting to connect to db with $dbConfig")
    val message = TestConnection(dbConfig.toDataSource)
    println(message)

    val result = Migrator(dbConfig.toDataSource)
    println(s"Number of successfully applied migrations $result")

    //FIXME(mpkocher)(2016-12-13) Requiring the JobsDao to have the jobResolver is not awesome
    val jobResolver = new PacBioIntJobResolver(engineConfig.pbRootJobDir)

    val dbURI = dbConfig.jdbcURI
    println(s"Postgres URL '$dbURI'")

    val db = dbConfig.toDatabase

    // This is not great that engine config and job resolver is
    // required to get the Summary of the database
    val jobsDao = new JobsDao(db, engineConfig, jobResolver)
    val summary = Await.result(jobsDao.getSystemSummary(), Duration.Inf)
    println(s"Summary for $db")
    println(summary)

    db.close()

    Right(ToolSuccess(toolId, computeTimeDelta(JodaDateTime.now(), startedAt)))
  }

}


object DatabaseToolApp extends App{

  import DatabaseTool._

  runner(args)
}
