package com.pacbio.secondary.smrtserver.tools

import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtserver.client.{AnalysisServiceAccessLayer,AnalysisClientJsonProtocol}
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
import scala.io.Source

import java.net.URL
import java.util.UUID
import java.io.{File, FileReader}

import com.pacbio.logging.{LoggerConfig, LoggerOptions}


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
                          block: Boolean = false,
                          command: CustomConfig => Unit = showDefaults,
                          datasetId: Either[Int, UUID] = Left(0),
                          jobId: Either[Int, UUID] = Left(0),
                          path: File = null,
                          name: String = "",
                          organism: String = "",
                          ploidy: String = "",
                          maxItems: Int = 25,
                          datasetType: String = "subreads") extends LoggerConfig


  lazy val defaults = CustomConfig(null, "localhost", 8070)

  lazy val parser = new OptionParser[CustomConfig]("pbservice") {

    private def validateId(entityId: String, entityType: String): Either[String, Unit] = {
      entityIdOrUuid(entityId) match {
        case Left(x) => if (x > 0) success else failure(s"${entityType} ID must be a positive integer or a UUID string")
        case Right(x) => success
      }
    }

    head("PacBio SMRTLink Services Client", VERSION)

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrtlink server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrtlink server"

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

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

    cmd(Modes.ANALYSIS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.ANALYSIS)
    } children(
      arg[File]("json-file") required() action { (p, c) =>
        c.copy(path = p)
      } text "JSON config file", // TODO validate json format
      opt[Boolean]("block") action { (_, c) =>
        c.copy(block = true)
      } text "Block until job completes"
    ) text "Run a pbsmrtpipe analysis pipeline from a JSON config file"

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


// TODO consolidate Try behavior
object PbServiceRunner extends LazyLogging {
  import AnalysisClientJsonProtocol._

  private val TIMEOUT = 10 seconds

  // FIXME this is crude
  private def errorExit(msg: String): Int = {
    println(msg)
    1
  }

  private def dsMetaTypeFromPath(path: String): String = {
    val ds = scala.xml.XML.loadFile(path)
    ds.attributes("MetaType").toString
  }

  private def dsUuidFromPath(path: String): UUID = {
    val ds = scala.xml.XML.loadFile(path)
    val uniqueId = ds.attributes("UniqueId").toString
    java.util.UUID.fromString(uniqueId)
  }

  private def showNumRecords(label: String, fn: () => Future[Seq[Any]]): Unit = {
    Try { Await.result(fn(), TIMEOUT) } match {
      case Success(records) => println(s"${label} ${records.size}")
      case Failure(err) => println("ERROR: couldn't retrieve ${label}")
    }
  }

  def runStatus(sal: AnalysisServiceAccessLayer): Int = {
    Try { Await.result(sal.getStatus, TIMEOUT) } match {
      case Success(status) => {
        println(s"Status ${status.message}")
        showNumRecords("SubreadSets", () => sal.getSubreadSets)
        showNumRecords("HdfSubreadSets", () => sal.getHdfSubreadSets)
        showNumRecords("ReferenceSets", () => sal.getReferenceSets)
        showNumRecords("BarcodeSets", () => sal.getBarcodeSets)
        showNumRecords("AlignmentSets", () => sal.getAlignmentSets)
        showNumRecords("import-dataset Jobs", () => sal.getImportJobs)
        showNumRecords("merge-dataset Jobs", () => sal.getMergeJobs)
        showNumRecords("convert-fasta-reference Jobs", () => sal.getFastaConvertJobs)
        showNumRecords("pbsmrtpipe Jobs", () => sal.getAnalysisJobs)
        0
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  private def printDataSetInfo(ds: DataSetMetaDataSet): Int = {
    println("DATASET SUMMARY:")
    println(s"  id: ${ds.id}")
    println(s"  uuid: ${ds.uuid}")
    println(s"  name: ${ds.name}")
    println(s"  path: ${ds.path}")
    println(s"  numRecords: ${ds.numRecords}")
    println(s"  totalLength: ${ds.totalLength}")
    println(s"  jobId: ${ds.jobId}")
    println(s"  md5: ${ds.md5}")
    println(s"  createdAt: ${ds.createdAt}")
    println(s"  updatedAt: ${ds.updatedAt}")
    0
  }

  def runGetDataSetInfo(sal: AnalysisServiceAccessLayer, datasetId: Either[Int, UUID]): Int = {
    Try { Await.result(sal.getDataSetByAny(datasetId), TIMEOUT) } match {
      case Success(ds) => printDataSetInfo(ds)
      case Failure(err) => errorExit(s"Could not retrieve existing dataset record: ${err}")
    }
  }

  def runGetDataSets(sal: AnalysisServiceAccessLayer, dsType: String, maxItems: Int): Int = {
    Try {
      dsType match {
        case "subreads" => Await.result(sal.getSubreadSets, TIMEOUT)
        case "hdfsubreads" => Await.result(sal.getHdfSubreadSets, TIMEOUT)
        case "barcodes" => Await.result(sal.getBarcodeSets, TIMEOUT)
        case "references" => Await.result(sal.getReferenceSets, TIMEOUT)
        //case _ => throw Exception("Not a valid dataset type")
      }
    } match {
      case Success(records) => {
        println(s"${records.size} records") // TODO print table
        0
      }
      case Failure(err) => {
        errorExit(s"Error: ${err.getMessage}")
      }
    }
  }

  private def printJobInfo(job: EngineJob): Int = {
    println("JOB SUMMARY:")
    println(s"  id: ${job.id}")
    println(s"  uuid: ${job.uuid}")
    println(s"  name: ${job.name}")
    println(s"  state: ${job.state}")
    println(s"  path: ${job.path}")
    println(s"  jobTypeId: ${job.jobTypeId}")
    println(s"  createdAt: ${job.createdAt}")
    println(s"  updatedAt: ${job.updatedAt}")
    job.createdBy match {
      case Some(createdBy) => println(s"  createdBy: ${createdBy}")
      case _ => println("  createdBy: none")
    }
    println(s"  comment: ${job.comment}")
    0
  }

  def runGetJobInfo(sal: AnalysisServiceAccessLayer, jobId: Either[Int, UUID]): Int = {
    Try { Await.result(sal.getJobByAny(jobId), TIMEOUT) } match {
      case Success(job) => printJobInfo(job)
      case Failure(err) => errorExit(s"Could not retrieve job record: ${err}")
    }
  }

  def runGetJobs(sal: AnalysisServiceAccessLayer, maxItems: Int): Int = {
    Try { Await.result(sal.getAnalysisJobs, TIMEOUT) } match {
      case Success(engineJobs) => {
        var i = 0
        // FIXME this is a poor approximation of the python program's output;
        // we need proper generic table formatting
        for (job <- engineJobs if i < maxItems) {
          println(f"${job.id}%8d ${job.state}%10s ${job.uuid} ${job.name}")
          i += 1
        }
        0
      }
      case Failure(err) => {
        errorExit(s"Could not retrieve jobs: ${err.getMessage}")
      }
    }
  }

  private def waitForJob(sal: AnalysisServiceAccessLayer, jobId: UUID): Int = {
    println("waiting for import job to complete...")
    Try { sal.pollForJob(jobId) } match {
      case Success(x) => runGetJobInfo(sal, Right(jobId))
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  def runImportFasta(sal: AnalysisServiceAccessLayer, path: String, name: String,
                     organism: String, ploidy: String): Int = {
    Try {
      Await.result(sal.importFasta(path, name, organism, ploidy), TIMEOUT)
    } match {
      case Success(job: EngineJob) => {
        println(job)
        waitForJob(sal, job.uuid) match {
          case 0 => {
            Try {
              Await.result(sal.getImportFastaJobDataStore(job.id), TIMEOUT)
            } match {
              case Success(dataStoreFiles) => {
                for (dsFile <- dataStoreFiles) {
                  if (dsFile.fileTypeId == "PacBio.DataSet.ReferenceSet") {
                    return runGetDataSetInfo(sal, Right(dsFile.uuid))
                  }
                }
                errorExit("Couldn't find ReferenceSet")
              }
              case Failure(err) => errorExit(s"Error retrieving import job datastore: ${err.getMessage}")
            }
          }
          case x => x
        }
      }
      case Failure(err) => errorExit(s"FASTA import failed: ${err.getMessage}")
    }
  }

  def runImportDataSetSafe(sal: AnalysisServiceAccessLayer, path: String): Int = {
    val dsUuid = dsUuidFromPath(path)
    println(s"UUID: ${dsUuid.toString}")

    Try { Await.result(sal.getDataSetByUuid(dsUuid), TIMEOUT) } match {
      case Success(dsInfo) => {
        println(s"Dataset ${dsUuid.toString} already imported.")
        printDataSetInfo(dsInfo)
      }
      case Failure(err) => {
        println(s"Could not retrieve existing dataset record: ${err}")
        //println(ex.getMessage)
        runImportDataSet(sal, path)
      }
    }
  }

  def runImportDataSet(sal: AnalysisServiceAccessLayer, path: String): Int = {
    val dsType = dsMetaTypeFromPath(path)
    logger.info(dsType)
    Try { Await.result(sal.importDataSet(path, dsType), TIMEOUT) } match {
      case Success(jobInfo: EngineJob) => {
        println(jobInfo)
        println("waiting for import job to complete...")
        val f = sal.pollForJob(jobInfo.uuid)
        // FIXME what happens if the job fails?
        runGetJobInfo(sal, Right(jobInfo.uuid))
      }
      case Failure(err) => {
        errorExit(s"Dataset import failed: ${err}")
      }
    }
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
    // FIXME can we embed this in the Json somehow?
    println("datasetId should be an integer; to obtain the datasetId from a UUID, run 'pbservice get-dataset {UUID}'. The entryId(s) can be obtained by running 'pbsmrtpipe show-pipeline-templates {PIPELINE-ID}'")
    0
  }

  def runAnalysisPipeline(sal: AnalysisServiceAccessLayer, jsonPath: String, block: Boolean): Int = {
    val jsonSrc = Source.fromFile(jsonPath).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    val analysisOptions = jsonAst.convertTo[PbSmrtPipeServiceOptions]
    println(analysisOptions)
    Try {
      Await.result(sal.getPipelineTemplateJson(analysisOptions.pipelineId), TIMEOUT)
    } match {
      case Success(x) => println(s"Found pipeline template ${analysisOptions.pipelineId}")
      case Failure(err) => {
        return errorExit(s"Can't find pipeline template ${analysisOptions.pipelineId}: ${err.getMessage}")
      }
    }
    for (entryPoint: BoundServiceEntryPoint <- analysisOptions.entryPoints) {
      Try {
        Await.result(sal.getDataSetById(entryPoint.datasetId), TIMEOUT)
      } match {
        case Success(dsInfo) => {
          // TODO check metatype against input
          println(s"Found entry point ${entryPoint.entryId} (datasetId = ${entryPoint.datasetId})")
          printDataSetInfo(dsInfo)
        }
        case Failure(err) => {
          return errorExit(s"can't retrieve datasetId ${entryPoint.datasetId}")
        }
      }
    }
    Try {
      Await.result(sal.runAnalysisPipeline(analysisOptions), TIMEOUT)
    } match {
      case Success(jobInfo) => {
        println(s"Job ${jobInfo.uuid} started")
        printJobInfo(jobInfo)
        if (block) waitForJob(sal, jobInfo.uuid) else 0
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  def apply (c: PbService.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val url = new URL(s"http://${c.host}:${c.port}")
    val sal = new AnalysisServiceAccessLayer(url)(actorSystem)
    val xc = c.mode match {
      case Modes.STATUS => runStatus(sal)
      case Modes.IMPORT_DS => runImportDataSetSafe(sal, c.path.getAbsolutePath)
      case Modes.IMPORT_FASTA => runImportFasta(sal, c.path.getAbsolutePath,
                                                c.name, c.organism, c.ploidy)
      case Modes.ANALYSIS => runAnalysisPipeline(sal, c.path.getAbsolutePath,
                                                 c.block)
      case Modes.TEMPLATE => runEmitAnalysisTemplate
      case Modes.JOB => runGetJobInfo(sal, c.jobId)
      case Modes.JOBS => runGetJobs(sal, c.maxItems)
      case Modes.DATASET => runGetDataSetInfo(sal, c.datasetId)
      case Modes.DATASETS => runGetDataSets(sal, c.datasetType, c.maxItems)
      case _ => errorExit("Unsupported action")
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
