package com.pacbio.secondary.smrtlink.tools

import ammonite.repl._

/**
  * Created by mkocher on 4/3/17.
  */
trait SmrtLinkReplTool {

  val predef = """
                 |import java.nio.file.{Files, Path, Paths}
                 |import java.util.UUID
                 |
                 |import scala.concurrent.{Await,Future}
                 |import scala.concurrent.duration._
                 |import scala.util.{Failure, Success, Try}
                 |
                 |import akka.actor.ActorSystem
                 |
                 |import org.joda.time.{DateTime => JodaDateTime}
                 |// For Json serialization
                 |import spray.json._
                 |
                 |println("Import SL core models, SL Client and DataSet loading utils")
                 |
                 |import slick.driver.PostgresDriver.api._
                 |
                 |import com.pacbio.secondary.smrtlink.models._
                 |import com.pacbio.common.models.CommonModelImplicits._
                 |import com.pacbio.secondary.smrtlink.client.{SmrtLinkServiceAccessLayer => S}
                 |import com.pacbio.secondary.analysis.datasets.io.ImplicitDataSetIOLoader._
                 |import com.pacbio.secondary.analysis.datasets.io.DataSetLoader._
                 |import com.pacbio.secondary.analysis.engine.EngineConfig
                 |import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, PacBioIntJobResolver}
                 |import com.pacbio.secondary.smrtlink.actors.JobsDao
                 |import com.pacbio.secondary.analysis.configloaders.ConfigLoader
                 |
                 |//This will make the repl hang on exit. Need to call actorSystem.shutdown()
                 |//implicit val actorSystem = ActorSystem("smrtlink-repl")
                 |
                 |object Pb extends ConfigLoader{
                 |  def runAndBlock[T](fx: => Future[T], timeOut: Duration = 10.seconds): T = Await.result(fx, timeOut)
                 |  def r[T](fx: => Future[T], timeOut: Duration = 10.seconds): T = Await.result(fx, timeOut)
                 |
                 |  def toDao(configKey: String = "smrtflow.db"): JobsDao = {
                 |     val db = Database.forConfig(configKey)
                 |     val engineConfig = EngineConfig(1, None, Paths.get("/tmp"), debugMode = true)
                 |     val resolver = new PacBioIntJobResolver(engineConfig.pbRootJobDir)
                 |     new JobsDao(db, engineConfig, resolver, None)
                 |  }
                 |  def toAC():ActorSystem = ActorSystem("smrtlink-repl")
                 |}
               """.stripMargin

  val welcomeBanner = Some("Welcome to the SMRT Link REPL")

  val repl = ammonite.Main(predef = predef, welcomeBanner = welcomeBanner)

}



object SmrtLinkReplApp extends App with SmrtLinkReplTool {

  repl.run()

}
