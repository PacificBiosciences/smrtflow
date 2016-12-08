package com.pacbio.secondary.smrtserver.tools

import java.net.URI
import java.nio.file.Paths

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.configloaders.EngineCoreConfigLoader
import com.pacbio.secondary.analysis.jobs.PacBioIntJobResolver
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import slick.driver.PostgresDriver.api._


case class DatabaseToolOptions(uri: URI) extends LoggerConfig

object DatabaseTool extends CommandLineToolRunner[DatabaseToolOptions] with EngineCoreConfigLoader{

  val toolId = "pbscala.tools.database_tool"
  val VERSION = "0.1.0"
  val DESCRIPTION =
    """
      |Display a terse of the contents of the database.
    """.stripMargin

  def toS(sx: String) = if (sx.startsWith("jdbc:sqlite:")) sx else s"jdbc:sqlite:$sx"
  def toURI(sx: String) = new URI(toS(sx))


  lazy val defaultURI =  toURI(conf.getString("pb-services.db-uri"))
  val defaults = DatabaseToolOptions(defaultURI)


  val parser = new OptionParser[DatabaseToolOptions]("database-tools") {
    head("")
    note(DESCRIPTION)
    opt[String]("db-uri") action { (x, c) => c.copy(uri = toURI(x))}

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def run(c: DatabaseToolOptions): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()

    val jobResolver = new PacBioIntJobResolver(Paths.get(engineConfig.pbRootJobDir))
    val db = Database.forURL(c.uri.toString, driver="org.postgresql.Driver")
    // This is not great that engine config and job resolver is
    // required to get the Summary of the database
    val jobsDao = new JobsDao(db, engineConfig, jobResolver)

    val result = Await.result(jobsDao.getSystemSummary(), Duration.Inf)

    println(s"Summary for $db")
    println(result)

    Right(ToolSuccess(toolId, computeTimeDelta(JodaDateTime.now(), startedAt)))
  }

}


object DatabaseToolApp extends App{

  import DatabaseTool._

  runner(args)
}
