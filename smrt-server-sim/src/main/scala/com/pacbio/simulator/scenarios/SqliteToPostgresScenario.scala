package com.pacbio.simulator.scenarios

import java.io.File
import java.net.URL
import java.nio.file.{Paths, Path}

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.database.DatabaseConfig
import com.pacbio.secondary.smrtlink.database.legacy.{SqliteToPostgresConverter, SqliteToPostgresConverterOptions}
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacbio.simulator.StepResult.{SUCCEEDED, Result}
import com.pacbio.simulator.steps.{ConditionalSteps, VarSteps, SmrtLinkSteps}
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.typesafe.config.{ConfigException, Config}

import scala.concurrent.Future

object SqliteToPostgresScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for SqliteToPostgresScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    val opts = SqliteToPostgresConverterOptions(
      // TODO(smcclellan): Construct golden sqlite db file (See SL-985)
      new File(c.getString("sqlite-file")),
      c.getString("user"),
      c.getString("password"),
      c.getString("db-name"),
      c.getString("server"),
      getInt("port"))

    new SqliteToPostgresScenario(Paths.get(c.getString("smrt-link-jar-file")), opts)
  }
}

class SqliteToPostgresScenario(smrtLinkJar: Path, opts: SqliteToPostgresConverterOptions)
  extends Scenario with VarSteps with ConditionalSteps with SmrtLinkSteps with TestUtils {

  override val name = "SqliteToPostgresScenario"

  override val smrtLinkClient = new SmrtLinkServiceAccessLayer(new URL("http", "localhost", 8081, ""))

  // TODO(smcclellan): Move these steps into ...simulator.steps package?

  case class RunSqliteToPostgresConverterStep(opts: Var[SqliteToPostgresConverterOptions]) extends Step {
    override val name = "RunSqliteToPostgresConverter"
    override def run: Future[Result] = Future {
      SqliteToPostgresConverter.runImporter(opts.get)
      SUCCEEDED
    }
  }

  case class LaunchSmrtLinkStep(pathToJar: Var[Path]) extends VarStep[Process] {
    override val name = "LaunchSmrtLink"
    override def run: Future[Result] = Future {
      // TODO(smcclellan): Pass in necessary configs with '-D'
      val cmd = s"java -jar ${pathToJar.get.toAbsolutePath}"
      output(Runtime.getRuntime.exec(cmd))
      SUCCEEDED
    }
  }

  case class KillSmrtLink(process: Var[Process]) extends VarStep[Int] {
    override val name = "KillSmrtLink"
    override def run: Future[Result] = Future {
      process.get.destroy()
      output(process.get.exitValue())
      SUCCEEDED
    }
  }

  val converterOpts: Var[SqliteToPostgresConverterOptions] = Var(opts)
  val smrtLinkJarPath: Var[Path] = Var(smrtLinkJar)
  val smrtLinkProcess: Var[Process] = Var()
  val smrtLinkExitCode: Var[Int] = Var()

  override def setUp() = {
    // TODO(smcclellan): Make maxConnections configurable?
    val dbConfig = DatabaseConfig(
      opts.pgDbName,
      opts.pgUsername,
      opts.pgPassword,
      opts.pgServer,
      opts.pgPort,
      maxConnections = 10)
    setupDb(dbConfig)
  }

  override val steps = Seq(
    RunSqliteToPostgresConverterStep(converterOpts),

    smrtLinkProcess := LaunchSmrtLinkStep(smrtLinkJarPath),

    // TODO(smcclellan): Add steps to verify that SMRTLink endpoints contain data from SQLite

    smrtLinkExitCode := KillSmrtLink(smrtLinkProcess),

    fail("SMRT Link exited with non-zero code") IF smrtLinkExitCode !=? 0
  )

  override def tearDown() = {
    // Kill SMRT Link if still running
    if (smrtLinkProcess.isDefined) {
      try {
        smrtLinkProcess.get.exitValue()
      } catch {
        case e: IllegalThreadStateException => smrtLinkProcess.get.destroy()
      }
    }
  }
}