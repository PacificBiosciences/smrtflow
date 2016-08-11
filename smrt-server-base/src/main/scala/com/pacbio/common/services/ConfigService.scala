package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.typesafe.config._
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

// TODO(smcclellan): Add unit tests
// TODO(smcclellan): Add .rst docs

class ConfigService extends PacBioService {
  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("config"),
    "Config Service",
    "0.1.0", "Subsystem Config Service")

  val routes = pathPrefix("config") {
    pathEnd {
      get {
        complete {
          ok {
            getResponse(List.empty[String])
          }
        }
      }
    } ~
    path(Segments) { configPath: List[String] =>
      get {
        complete {
          ok {
            getResponse(configPath)
          }
        }
      }
    }
  }

  def getResponse(configPath: List[String]): Try[ConfigResponse] = {
    import ConfigValueType.OBJECT

    def stripQuotes(s: String): String = s.replaceAll("^\"|\"$", "")

    var traversed: String = ""
    var conf: Try[Config] = Try {
      ConfigFactory.load()
    }

    configPath.dropRight(1).foreach { p =>
      conf = conf.flatMap { c => Try {
        if (c.hasPath(p)) {
          if (traversed.isEmpty) traversed = p else traversed = traversed + "." + p
          c.getConfig(p)
        }
        else
          throw new ResourceNotFoundError(s"Unable to find resource '$p' at path '$traversed'")
      }}
    }
    val leafConf: Try[ConfigValue] = conf.flatMap { c => Try {
      configPath.lastOption match {
        case Some(p) =>
          if (c.hasPath(p)) {
            if (traversed.isEmpty) traversed = p else traversed = traversed + "." + p
            c.getValue(p)
          }
          else
            throw new ResourceNotFoundError(s"Unable to find resource '$p' at path '$traversed'")
        case None => c.root()
      }
    }}

    leafConf.map { c =>
      val entries: Set[ConfigEntry] = c.valueType() match {
        case OBJECT =>
          c.asInstanceOf[ConfigObject].toConfig.entrySet().map { e =>
            ConfigEntry(
              traversed + "." + e.getKey,
              stripQuotes(e.getValue.render()))
          }.toSet
        case _ => Set(ConfigEntry(traversed, stripQuotes(c.render())))
      }
      ConfigResponse(entries, c.origin().description())
    }
  }
}

trait ConfigServiceProvider {
  val configService: Singleton[ConfigService] = Singleton(() => new ConfigService).bindToSet(AllServices)
}

trait ConfigServiceProviderx {
  this: ServiceComposer =>

  val configService: Singleton[ConfigService] = Singleton(() => new ConfigService)

  addService(configService)
}
