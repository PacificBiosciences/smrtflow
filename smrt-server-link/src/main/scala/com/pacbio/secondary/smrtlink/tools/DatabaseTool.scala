package com.pacbio.secondary.smrtlink.tools

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.configloaders.EngineCoreConfigLoader
import com.pacbio.secondary.smrtlink.analysis.jobs.PacBioIntJobResolver
import com.pacbio.secondary.smrtlink.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.database.{DatabaseConfig, DatabaseUtils}
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object DbModes {
  sealed trait Mode {
    val name: String
  }
  case object STATUS extends Mode {val name = "status"}
  case object MIGRATE extends Mode {val name = "migrate"}
  case object SUMMARY extends Mode {val name = "summary"}
  case object UNKNOWN extends Mode {val name = "unknown"}
}

case class DatabaseToolOptions(username: String,
                               password: String,
                               dbName: String,
                               server: String,
                               port: Int = 5432,
                               mode: DbModes.Mode = DbModes.UNKNOWN) extends LoggerConfig

object DatabaseTool extends CommandLineToolRunner[DatabaseToolOptions] with EngineCoreConfigLoader{

  import DatabaseUtils._

  val toolId = "pbscala.tools.database_tool"
  val VERSION = "0.2.0"
  val DESCRIPTION =
    """
      |Utility for interacting with SMRT Link database backend
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

    cmd(DbModes.STATUS.name) action { (_, c) =>
      c.copy(mode = DbModes.STATUS)
    } text "Check connectivity and display database status"
    cmd(DbModes.MIGRATE.name) action { (_, c) =>
      c.copy(mode = DbModes.MIGRATE)
    } text "Migrate SQLite tables to Postgres"
    cmd(DbModes.SUMMARY.name) action { (_, c) =>
      c.copy(mode = DbModes.SUMMARY)
    } text "Display database summary"

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

  def runStatus(dbConfig: DatabaseConfig): DatabaseConfig = {
    println(s"Attempting to connect to db with $dbConfig")
    val message = TestConnection(dbConfig.toDataSource)
    println(message)
    dbConfig
  }

  def runMigrate(dbConfig: DatabaseConfig): Unit = {
    val result = Migrator(dbConfig.toDataSource)
    println(s"Number of successfully applied migrations $result")
    runSummary(dbConfig)
  }

  def runSummary(dbConfig: DatabaseConfig): Unit = {
    //FIXME(mpkocher)(2016-12-13) Requiring the JobsDao to have the jobResolver is not awesome
    val jobResolver = new PacBioIntJobResolver(engineConfig.pbRootJobDir)

    val dbURI = dbConfig.jdbcURI
    println(s"Postgres URL '$dbURI'")

    val db = dbConfig.toDatabase

    // This is not great that engine config and job resolver is
    // required to get the Summary of the database
    val jobsDao = new JobsDao(db, jobResolver)
    val summary = Await.result(jobsDao.getSystemSummary(), Duration.Inf)
    println(s"Summary for $db")
    println(summary)

    db.close()
  }

  def run(c: DatabaseToolOptions): Either[ToolFailure, ToolSuccess] = {

    val dbConfig = DatabaseConfig(c.dbName, c.username, c.password, c.server, c.port)
    val startedAt = JodaDateTime.now()
    val runStatusMigrate = runStatus _ andThen runMigrate
    val runStatusSummary = runStatus _ andThen runSummary
    c.mode match {
      case DbModes.STATUS => runStatus(dbConfig)
      case DbModes.MIGRATE => runStatusMigrate(dbConfig)
      case DbModes.SUMMARY => runStatusSummary(dbConfig)
      case x => println(s"Unsupported action '$x'")
    }

    Right(ToolSuccess(toolId, computeTimeDelta(JodaDateTime.now(), startedAt)))
  }

}


object DatabaseToolApp extends App{

  import DatabaseTool._

  runner(args)
}
