package com.pacbio.secondary.lims.tools


import java.io.File
import java.net.URL
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import com.pacbio.common.models.ServiceStatus
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondaryinternal.IOUtils
import com.pacbio.secondaryinternal.client.InternalAnalysisServiceClient
import com.pacbio.secondaryinternal.models.{ReseqConditions, ServiceConditionCsvPipeline}
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

import com.pacbio.secondaryinternal.tools

/**
 * Modes
 *   - Status
 *   - Convert CSV to ReseqConditionJson
 *   - Submit Job from ReseqConditionJson
 *
 *
 */
object Modes {
  sealed trait Mode { val name: String }
  case object IMPORT extends Mode { val name = "import"}
  case object FIND_AND_IMPORT extends Mode { val name = "find_and_import"}
  case object RESOLVE_EXPID extends Mode { val name = "resolve_expid"}
  case object RESOLVE_RUNCODE extends Mode { val name = "resolve_runcode"}
  case object RESOLVE_UUID extends Mode { val name = "resolve_uuid"}
  case object UNKNOWN extends Mode { val name = "unknown"}
}

case class CustomConfig(
    mode: Modes.Mode = Modes.UNKNOWN,
    command: CustomConfig => Unit,
    host: String = "http://smrt-lims",
    port: Int = 8081,
    path: Path = Paths.get(".")) extends LoggerConfig

trait LimsClientToolRunner extends CommonClientToolRunner { // TODO: move CommonClientToolRunner out of analysis? ATM, it is the only dep requiring smrtServerAnalysis

  def runImport(host: String, port: Int): Int =
    runAwaitWithActorSystem[ServiceStatus](defaultSummary[ServiceStatus]){ (system: ActorSystem) =>
      val client = new InternalAnalysisServiceClient(host, port)(system)
      client.getStatus
    }
}

/**
 * Created by jfalkner on 7/27/16.
 */
class LimsClientToolsApp {

}
