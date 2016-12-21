package com.pacbio.secondary.analysis.configloaders

import java.nio.file.{Files, Paths}

import com.pacbio.secondary.analysis.engine.EngineConfig
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}

/**
 *
 * Core Engine Configuration Loader
 *
 * - Max Workers is the total number of concurrent job types that will be run
 * - Root Job dir is the root directory where the jobs will be run in
 * -
 *
 * Created by mkocher on 10/8/15.
 */
trait EngineCoreConfigConstants {
  val MAX_WORKERS = "smrtflow.engine.max-workers"
  val PB_TOOLS_ENV = "smrtflow.engine.pb-tools-env"
  val PB_ROOT_JOB_DIR = "smrtflow.engine.jobs-root"
  val DEBUG_MODE = "smrtflow.engine.debug-mode"
}

object EngineCoreConfigConstants extends EngineCoreConfigConstants

trait ConfigLoader {
  // If -Dconf.file=/path/to/file.conf is used, then
  // the conf file is **only** used. Use the "import" within
  // the file.conf to load other configs (e.g., import "reference.conf")
  lazy val conf = ConfigFactory.load()
}

trait EngineCoreConfigLoader extends ConfigLoader with LazyLogging{

  private lazy val defaultEngineDebugMode = conf.getBoolean(EngineCoreConfigConstants.DEBUG_MODE)
  private lazy val defaultEngineMaxWorkers = conf.getInt(EngineCoreConfigConstants.MAX_WORKERS)
  private lazy val defaultEngineJobsRoot = loadJobRoot(conf.getString(EngineCoreConfigConstants.PB_ROOT_JOB_DIR))

  lazy val defaultEngineConfig = EngineConfig(
    defaultEngineMaxWorkers,
    "",
    defaultEngineJobsRoot,
    debugMode = defaultEngineDebugMode)

  /**
   * Load
   * @param sx
   * @return
   */
  def loadJobRoot(sx: String): String = {
    val p = Paths.get(sx)
    if (!p.isAbsolute) {
      //Assume relative to launched directory
      //println(s"Warning. Assuming relative path ${System.getProperty("user.dir")}/$sx")
      Paths.get(System.getProperty("user.dir")).toAbsolutePath.resolve(sx).toString
    } else {
      p.toAbsolutePath.toString
    }
  }

  // FIXME. Convert this to Option[Path]
  private def loadPbToolsEnv(conf: Config): String = {
    Try { conf.getString(EngineCoreConfigConstants.PB_TOOLS_ENV) } match {
      case Success(p) =>
        logger.info(s"Loaded ${EngineCoreConfigConstants.PB_TOOLS_ENV} -> $p")
        p
      case Failure(ex) =>
        logger.warn(s"Failed to find or load ${EngineCoreConfigConstants.PB_TOOLS_ENV}. Using default value")
        ""
    }
  }

  def loadFromAppConf: EngineConfig = {
    val pbRootJobDir = loadJobRoot(conf.getString(EngineCoreConfigConstants.PB_ROOT_JOB_DIR))
    val pbToolsEnv = loadPbToolsEnv(conf)
    val c = defaultEngineConfig.copy(pbRootJobDir = pbRootJobDir, pbToolsEnv = pbToolsEnv)
    logger.info(s"Loaded Engine config $c")
    c
  }

  lazy final val engineConfig = loadFromAppConf

}

object EngineCoreConfigLoader extends EngineCoreConfigLoader