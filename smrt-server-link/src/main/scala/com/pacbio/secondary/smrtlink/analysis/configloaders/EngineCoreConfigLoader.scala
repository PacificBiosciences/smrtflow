package com.pacbio.secondary.smrtlink.analysis.configloaders

import java.nio.file.{Files, Path, Paths}

import com.pacbio.secondary.smrtlink.models.EngineConfig
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
  val MAX_WORKERS = "smrtflow.engine.maxWorkers"
  val PB_TOOLS_ENV = "smrtflow.engine.pb-tools-env"
  val PB_ROOT_JOB_DIR = "smrtflow.engine.jobRootDir"
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
  private lazy val defaultPbToolsEnv = loadPbToolsEnv(conf)

  lazy val defaultEngineConfig = EngineConfig(
    defaultEngineMaxWorkers,
    defaultPbToolsEnv,
    defaultEngineJobsRoot,
    debugMode = defaultEngineDebugMode)

  /**
   * Load
   * @param sx
   * @return
   */
  def loadJobRoot(sx: String): Path = {
    val p = Paths.get(sx)
    if (!p.isAbsolute) {
      //Assume relative to launched directory
      //println(s"Warning. Assuming relative path ${System.getProperty("user.dir")}/$sx")
      Paths.get(System.getProperty("user.dir")).toAbsolutePath.resolve(sx)
    } else {
      p.toAbsolutePath
    }
  }

  private def loadPbToolsEnv(conf: Config): Option[Path] =
    Try { Paths.get(conf.getString(EngineCoreConfigConstants.PB_TOOLS_ENV)).toAbsolutePath }.toOption

  def loadFromAppConf: EngineConfig = {
    val c = defaultEngineConfig
    logger.info(s"Loaded Engine config $c")
    c
  }

  lazy final val engineConfig = loadFromAppConf

}

object EngineCoreConfigLoader extends EngineCoreConfigLoader