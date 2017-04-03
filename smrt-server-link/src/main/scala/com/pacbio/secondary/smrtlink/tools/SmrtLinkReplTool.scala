package com.pacbio.secondary.smrtlink.tools

import ammonite.repl._

/**
  * Created by mkocher on 4/3/17.
  */
trait SmrtLinkReplTool {

  val predef = """
                 |import java.nio.file.{Files, Path, Paths}
                 |import java.util.UUID
                 |import scala.concurrent.Await
                 |import scala.concurrent.duration._
                 |import akka.actor.ActorSystem
                 |println("Import SL core models, SL Client and DataSet loading utils")
                 |import com.pacbio.secondary.smrtlink.models._
                 |import com.pacbio.common.models.CommonModelImplicits._
                 |import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
                 |import com.pacbio.secondary.analysis.datasets.io.ImplicitDataSetIOLoader._
                 |import com.pacbio.secondary.analysis.datasets.io.DataSetLoader._
                 |//This will make the repl hang on exit. Need to call actorSystem.shutdown()
                 |//implicit val actorSystem = ActorSystem("smrtlink-repl")
               """.stripMargin

  val welcomeBanner = Some("Welcome to the SMRT Link REPL")

  val repl = ammonite.Main(predef = predef, welcomeBanner = welcomeBanner)

}



object SmrtLinkReplApp extends App with SmrtLinkReplTool {

  repl.run()

}
