package com.pacbio.secondary.smrttools.tools

import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.smrttools.client.{ServiceAccessLayer,ServicesClientJsonProtocol}
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, PbSmrtPipeServiceOptions, ServiceTaskOptionBase}

import akka.actor.ActorSystem
import org.joda.time.DateTime
import scopt.OptionParser
import com.typesafe.scalalogging.LazyLogging
import spray.httpx
import spray.json._
import spray.httpx.SprayJsonSupport


import scala.collection.mutable
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import java.net.URL
import java.util.UUID
import java.io.File


object Modes {
  sealed trait Mode {
    val name: String
  }
  case object STATUS extends Mode {val name = "status"}
  case object IMPORT_DS extends Mode {val name = "import-dataset"}
  case object IMPORT_FASTA extends Mode {val name = "import-fasta"}
  case object ANALYSIS extends Mode {val name = "run-analysis"}
  case object TEMPLATE extends Mode {val name = "emit-analysis-template"}
  case object JOB extends Mode {val name = "get-job"}
  case object JOBS extends Mode {val name = "get-jobs"}
  case object DATASET extends Mode {val name = "get-dataset"}
  case object DATASETS extends Mode {val name = "get-datasets"}
  case object UNKNOWN extends Mode {val name = "unknown"}
}

object PbService {
  val VERSION = "0.1.0"
  var TOOL_ID = "pbscala.tools.pbservice"
  private val MAX_FASTA_SIZE = 100.0 // megabytes

  private def getSizeMb(fileObj: File): Double = {
    fileObj.length / 1024.0 / 1024.0
  }

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  // is there a cleaner way to do this?
  private def entityIdOrUuid(entityId: String): Either[Int, UUID] = {
    try {
      Left(entityId.toInt)
    } catch {
      case e: Exception => {
        try {
          Right(UUID.fromString(entityId))
        } catch {
          case e: Exception => Left(0)
        }
      }
    }
  }

  case class CustomConfig(mode: Modes.Mode = Modes.UNKNOWN,
                          host: String,
                          port: Int,
                          debug: Boolean = false,
                          command: CustomConfig => Unit = showDefaults,
                          datasetId: Either[Int, UUID] = Left(0),
                          jobId: Either[Int, UUID] = Left(0),
                          path: File = null,
                          name: String = "",
                          organism: String = "",
                          ploidy: String = "",
                          maxItems: Int = 25,
                          datasetType: String = "subreads")


  lazy val defaults = CustomConfig(null, "localhost", 8070, debug=false)

  lazy val parser = new OptionParser[CustomConfig]("pbservice") {

    private def validateId(entityId: String, entityType: String): Either[String, Unit] = {
      entityIdOrUuid(entityId) match {
        case Left(x) => if (x > 0) success else failure(s"${entityType} ID must be a positive integer or a UUID string")
        case Right(x) => success
      }
    }

    head("PacBio SMRTLink Services Client", VERSION)

    opt[Boolean]("debug") action { (v,c) =>
      c.copy(debug=true)
    } text "Debug mode"
    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrtlink server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrtlink server"

    cmd(Modes.STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.STATUS)
    }

    cmd(Modes.IMPORT_DS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_DS)
    } children(
      arg[File]("dataset-path") required() action { (p, c) =>
        c.copy(path = p)
      } text "DataSet XML path"
    ) text "Import DataSet XML"

    cmd(Modes.IMPORT_FASTA.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_FASTA)
    } children(
      arg[File]("fasta-path") required() action { (p, c) =>
        c.copy(path = p)
      } validate { p => {
          val size = getSizeMb(p)
          // it's great that we can do this, but it would be more awesome if
          // scopt didn't have to print the --help output after it
          if (size < MAX_FASTA_SIZE) success else failure(s"Fasta file is too large ${size} MB > ${MAX_FASTA_SIZE} MB. Create a ReferenceSet using fasta-to-reference, then import using `pbservice import-dataset /path/to/referenceset.xml")
        }
      } text "FASTA path",
      arg[String]("reference-name") action { (name, c) =>
        c.copy(name = name) // do we need to check that this is non-blank?
      } text "Name of ReferenceSet",
      opt[String]("organism") action { (organism, c) =>
        c.copy(organism = organism)
      } text "Organism",
      opt[String]("ploidy") action { (ploidy, c) =>
        c.copy(ploidy = ploidy)
      } text "Ploidy"
    ) text "Import Reference FASTA"

    cmd(Modes.TEMPLATE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TEMPLATE)
    } children(
    ) text "Emit an analysis.json template to stdout that can be run using 'run-analysis'"

    cmd(Modes.JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.JOB)
    } children(
      arg[String]("job-id") required() action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } validate { i => validateId(i, "Job") } text "Job ID"
    ) text "Show job details"

    cmd(Modes.JOBS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.JOBS)
    } children(
      opt[Int]('m', "max-items") action { (m, c) =>
        c.copy(maxItems = m)
      } text "Max number of jobs to show"
    )

    cmd(Modes.DATASET.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DATASET)
    } children(
      arg[String]("dataset-id") required() action { (i, c) =>
        c.copy(datasetId = entityIdOrUuid(i))
      } validate { i => validateId(i, "Dataset") } text "Dataset ID"
    ) text "Show dataset details"

    cmd(Modes.DATASETS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DATASETS)
    } children(
      opt[String]('t', "dataset-type") action { (t, c) =>
        c.copy(datasetType = t)
      } text "Dataset Meta type", // TODO validate
      opt[Int]('m', "max-items") action { (m, c) =>
        c.copy(maxItems = m)
      } text "Max number of Datasets to show"
    )
  }
}


object PbServiceRunner extends LazyLogging {
  import ServicesClientJsonProtocol._

  private def dsMetaTypeFromPath(path: String): String = {
    val ds = scala.xml.XML.loadFile(path)
    ds.attributes("MetaType").toString
  }

  private def dsUuidFromPath(path: String): UUID = {
    val ds = scala.xml.XML.loadFile(path)
    val uniqueId = ds.attributes("UniqueId").toString
    java.util.UUID.fromString(uniqueId)
  }

  def runStatus(sal: ServiceAccessLayer): Int = {
    val fx = for {
      status <- sal.getStatus
    } yield (status)

    val results = Await.result(fx, 5 seconds)
    val (status) = results
    println(status)
    0
  }

  def runGetDataSetInfo(sal: ServiceAccessLayer, datasetId: Either[Int, UUID]): Int = {
    var xc = 0
    var result = Try { Await.result(sal.getDataSetByAny(datasetId), 5 seconds) }
    result match {
      case Success(dsInfo) => {
        println(dsInfo)
      }
      case Failure(err) => {
        println(s"Could not retrieve existing dataset record: ${err}")
        xc = 1
      }
    }
    xc
  }

  def runGetDataSets(sal: ServiceAccessLayer, dsType: String, maxItems: Int): Int = {
    var xc = 0
    var result =  Try {
      dsType match {
        case "subreads" => Await.result(sal.getSubreadSets(), 5 seconds)
        case "hdfsubreads" => Await.result(sal.getHdfSubreadSets(), 5 seconds)
        case "barcodes" => Await.result(sal.getBarcodeSets(), 5 seconds)
        case "references" => Await.result(sal.getReferenceSets(), 5 seconds)
        //case _ => throw Exception("Not a valid dataset type")
      }
    }
    result match {
      case Success(records) => {
        println(s"${records.size} records")
      }
      case Failure(err) => {
        println(s"Error: ${err.getMessage}")
        xc = 1
      }
    }
    xc
  }

  def runGetJobInfo(sal: ServiceAccessLayer, jobId: Either[Int, UUID]): Int = {
    var xc = 0
    var result = Try { Await.result(sal.getJobByAny(jobId), 5 seconds) }
    result match {
      case Success(jobInfo) => {
        println(jobInfo)
      }
      case Failure(err) => {
        println(s"Could not retrieve job record: ${err}")
        xc = 1
      }
    }
    xc
  }

  def runImportFasta(sal: ServiceAccessLayer, path: String, name: String,
                     organism: String, ploidy: String): Int = {
    var xc = 0
    var result = Try {
      Await.result(sal.importFasta(path, name, organism, ploidy), 5 seconds)
    }
    result match {
      case Success(jobInfo: EngineJob) => {
        println(jobInfo)
        println("waiting for import job to complete...")
        val f = sal.pollForJob(jobInfo.uuid)
        // FIXME what happens if the job fails?
        xc = runGetJobInfo(sal, Right(jobInfo.uuid))
      }
      case Failure(err) => {
        println(s"FASTA import failed: ${err.getMessage}")
        xc = 1
      }
    }
    xc
  }

  def runImportDataSetSafe(sal: ServiceAccessLayer, path: String): Int = {
    val dsUuid = dsUuidFromPath(path)
    println(s"UUID: ${dsUuid.toString}")

    var xc = 0
    var dsInfo = Try { Await.result(sal.getDataSetByUuid(dsUuid), 5 seconds) }
    dsInfo match {
      case Success(x) => {
        println(s"Dataset ${dsUuid.toString} already imported.")
        println(dsInfo)
      }
      case Failure(err) => {
        println(s"Could not retrieve existing dataset record: ${err}")
        //println(ex.getMessage)
        xc = runImportDataSet(sal, path)
      }
    }
    xc
  }

  def runImportDataSet(sal: ServiceAccessLayer, path: String): Int = {
    val dsType = dsMetaTypeFromPath(path)
    logger.info(dsType)
    var xc = 0
    val result = Try { Await.result(sal.importDataSet(path, dsType), 5 seconds) }
    result match {
      case Success(jobInfo: EngineJob) => {
        println(jobInfo)
        println("waiting for import job to complete...")
        val f = sal.pollForJob(jobInfo.uuid)
        // FIXME what happens if the job fails?
        xc = runGetJobInfo(sal, Right(jobInfo.uuid))
      }
      case Failure(err) => {
        println(s"Dataset import failed: ${err}")
        xc = 1
      }
    }
    xc
  }

  def runEmitAnalysisTemplate: Int = {
    val analysisOpts = {
      val ep = BoundServiceEntryPoint("eid_subread", "PacBio.DataSet.SubreadSet", 1)
      val eps = Seq(ep)
      val taskOptions = Seq[ServiceTaskOptionBase]()
      val workflowOptions = Seq[ServiceTaskOptionBase]()
      PbSmrtPipeServiceOptions(
        "My-job-name",
        "pbsmrtpipe.pipelines.mock_dev01",
        eps,
        taskOptions,
        workflowOptions)
    }
    println(analysisOpts.toJson.prettyPrint)
    0
  }

  def apply (c: PbService.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val url = new URL(s"http://${c.host}:${c.port}")
    val sal = new ServiceAccessLayer(url)(actorSystem)
    val xc = c.mode match {
      case Modes.STATUS => runStatus(sal)
      case Modes.IMPORT_DS => runImportDataSetSafe(sal, c.path.getAbsolutePath)
      case Modes.IMPORT_FASTA => runImportFasta(sal, c.path.getAbsolutePath,
                                                c.name, c.organism, c.ploidy)
      case Modes.JOB => runGetJobInfo(sal, c.jobId)
      case Modes.DATASET => runGetDataSetInfo(sal, c.datasetId)
      case Modes.DATASETS => runGetDataSets(sal, c.datasetType, c.maxItems)
      case Modes.TEMPLATE => runEmitAnalysisTemplate
      case _ => {
        println("Unsupported action")
        1
      }
    }
    actorSystem.shutdown()
    xc
  }

}

object PbServiceApp extends App {
  def run(args: Seq[String]) = {
    val xc = PbService.parser.parse(args.toSeq, PbService.defaults) match {
      case Some(config) => PbServiceRunner(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
