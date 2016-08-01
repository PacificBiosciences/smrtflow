package com.pacbio.simulator.scenarios

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.simulator.steps._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.typesafe.config.{Config, ConfigException}
import spray.json._

/**
 * Example config:
 *
 * {{{
 *   smrt-link-host = "smrtlink-bihourly"
 *   smrt-link-port = 8081
 *   run-xml-path = "/path/to/testdata/runDataModel.xml"
 * }}}
 */
object RunDesignScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for RunDesignScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new RunDesignScenario(
      c.getString("smrt-link-host"),
      getInt("smrt-link-port"),
      Paths.get(c.getString("run-xml-path")))
  }
}

class RunDesignScenario(host: String, port: Int, runXmlFile: Path)
  extends Scenario with VarSteps with IOSteps with HttpSteps with RunDesignSteps {

  override val name = "RunDesignScenario"

  override val defaultHost = host
  override val defaultPort = port

  val runXmlPath: Var[String] = Var(runXmlFile.toString)
  val runXml: Var[String] = Var()
  val runResp: Var[String] = Var()
  val uid: Var[UUID] = runResp.mapWith { j =>
    UUID.fromString(j.parseJson.asJsObject.fields("uniqueId").compactPrint.stripPrefix("\"").stripSuffix("\""))
  }

  override val steps = Seq(
    // Read XML from file
    runXml := ReadFileStep(runXmlPath),

    // Post XML to SMRTLink
    runResp := CreateRunDesign(runXml),

    // Check that run design exists
    GetRunDesign(uid),

    // Delete run design
    DeleteRunDesign(uid)
  )
}
