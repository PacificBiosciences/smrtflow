package com.pacbio.secondary.smrtserver.tools

import com.pacbio.secondary.smrtserver.client.{AnalysisServiceAccessLayer,AnalysisClientJsonProtocol}
import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.analysis.pipelines._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.converters._
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, PbSmrtPipeServiceOptions, ServiceTaskOptionBase}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.models.{ServiceStatus}

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
import scala.math._

import java.net.URL
import java.util.UUID
import java.io.{File, FileReader}
import java.nio.file.Paths

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
  case object PIPELINE extends Mode {val name = "run-pipeline"}
  case object JOB extends Mode {val name = "get-job"}
  case object JOBS extends Mode {val name = "get-jobs"}
  case object DATASET extends Mode {val name = "get-dataset"}
  case object DATASETS extends Mode {val name = "get-datasets"}
  case object UNKNOWN extends Mode {val name = "unknown"}
}

object PbServiceParser {
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

  case class CustomConfig(
      mode: Modes.Mode = Modes.UNKNOWN,
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
      datasetType: String = "subreads",
      nonLocal: Option[String] = None,
      asJson: Boolean = false,
      pipelineId: String = "",
      jobTitle: String = "",
      entryPoints: Seq[String] = Seq(),
      presetXml: File = null,
      maxTime: Int = -1) extends LoggerConfig


  lazy val defaults = CustomConfig(null, "localhost", 8070, maxTime=600)

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

    opt[Unit]("json") action { (_, c) =>
      c.copy(asJson = true)
    } text "Display output as raw JSON"

    opt[Unit]("debug") action { (_, c) =>
      c.asInstanceOf[LoggerConfig].configure(c.logbackFile, c.logFile, true, c.logLevel).asInstanceOf[CustomConfig]
    } text "Display debugging log output"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

    cmd(Modes.STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.STATUS)
    }

    cmd(Modes.IMPORT_DS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_DS)
    } children(
      arg[File]("dataset-path") required() action { (p, c) =>
        c.copy(path = p)
      } text "DataSet XML path (or directory containing datasets)",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t)
      } text "Maximum time to poll for running job status",
      opt[String]("non-local") action { (t, c) =>
        c.copy(nonLocal = Some(t))
      } text "Import non-local dataset with specified type (e.g. PacBio.DataSet.SubreadSet)"
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
      opt[String]("name") action { (name, c) =>
        c.copy(name = name) // do we need to check that this is non-blank?
      } text "Name of ReferenceSet",
      opt[String]("organism") action { (organism, c) =>
        c.copy(organism = organism)
      } text "Organism",
      opt[String]("ploidy") action { (ploidy, c) =>
        c.copy(ploidy = ploidy)
      } text "Ploidy",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t)
      } text "Maximum time to poll for running job status"
    ) text "Import Reference FASTA"

    cmd(Modes.ANALYSIS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.ANALYSIS)
    } children(
      arg[File]("json-file") required() action { (p, c) =>
        c.copy(path = p)
      } text "JSON config file", // TODO validate json format
      opt[Unit]("block") action { (_, c) =>
        c.copy(block = true)
      } text "Block until job completes",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t)
      } text "Maximum time to poll for running job status"
    ) text "Run a pbsmrtpipe analysis pipeline from a JSON config file"

    cmd(Modes.TEMPLATE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TEMPLATE)
    } children(
    ) text "Emit an analysis.json template to stdout that can be run using 'run-analysis'"

    cmd(Modes.PIPELINE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.PIPELINE)
    } children(
      arg[String]("pipeline-id") required() action { (p, c) =>
        c.copy(pipelineId = p)
      } text "Pipeline ID to run",
      opt[String]('e', "entry-point") minOccurs(1) maxOccurs(1024) action { (e, c) =>
        c.copy(entryPoints = c.entryPoints :+ e)
      } text "Entry point (must be valid PacBio DataSet)",
      opt[File]("preset-xml") action { (x, c) =>
        c.copy(presetXml = x)
      } text "XML file specifying pbsmrtpipe options",
      opt[String]("job-title") action { (t, c) =>
        c.copy(jobTitle = t)
      } text "Job title (will be displayed in UI)",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t)
      } text "Maximum time to poll for running job status"
    ) text "Run a pbsmrtpipe pipeline by name on the server"

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

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"
  }
}


// TODO consolidate Try behavior
class PbService (val sal: AnalysisServiceAccessLayer,
                 val maxTime: Int = -1) extends LazyLogging {
  import AnalysisClientJsonProtocol._

  protected val TIMEOUT = 10 seconds
  private lazy val entryPointsLookup = Map(
    "PacBio.DataSet.SubreadSet" -> "eid_subread",
    "PacBio.DataSet.ReferenceSet" -> "eid_ref_dataset",
    "PacBio.DataSet.BarcodeSet" -> "eid_barcode",
    "PacBio.DataSet.HdfSubreadSet" -> "eid_hdfsubread",
    "PacBio.DataSet.ConsensusReadSet" -> "eid_ccs",
    "PacBio.DataSet.AlignmentSet" -> "eid_alignment")
  private lazy val defaultPresets = PipelineTemplatePreset("default", "any",
    Seq[PipelineBaseOption](),
    Seq[PipelineBaseOption]())


  // FIXME this is crude
  protected def errorExit(msg: String): Int = {
    println(msg)
    1
  }

  protected def printMsg(msg: String): Int = {
    println(msg)
    0
  }

  protected def dsMetaTypeFromPath(path: String): String = {
    val ds = scala.xml.XML.loadFile(path)
    ds.attributes("MetaType").toString
  }

  protected def dsUuidFromPath(path: String): UUID = {
    val ds = scala.xml.XML.loadFile(path)
    val uniqueId = ds.attributes("UniqueId").toString
    java.util.UUID.fromString(uniqueId)
  }

  protected def showNumRecords(label: String, fn: () => Future[Seq[Any]]): Unit = {
    Try { Await.result(fn(), TIMEOUT) } match {
      case Success(records) => println(s"${label} ${records.size}")
      case Failure(err) => println("ERROR: couldn't retrieve ${label}")
    }
  }

  protected def printStatus(status: ServiceStatus, asJson: Boolean = false): Int = {
    if (asJson) {
      println(status.toJson.prettyPrint)
    } else{
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
    }
    0
  }

  protected def printDataSetInfo(ds: DataSetMetaDataSet, asJson: Boolean = false): Int = {
    if (asJson) println(ds.toJson.prettyPrint) else {
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
    }
    0
  }

  protected def printJobInfo(job: EngineJob, asJson: Boolean = false): Int = {
    if (asJson) println(job.toJson.prettyPrint) else {
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
    }
    0
  }

  // TODO this is extremely general, move it somewhere central
  protected def printTable(table: Seq[Seq[String]], headers: Seq[String]): Int = {
    val columns = table.transpose
    val widths = for ((col, header) <- columns zip headers) yield {
      max(header.length, (for (cell <- col) yield cell.length).reduceLeft(_ max _))
    }
    val mkline = (row: Seq[String]) => for ((c, w) <- row zip widths) yield c.padTo(w, ' ')
    println(mkline(headers).mkString(" "))
    for (row <- table) {
      println(mkline(row).mkString(" "))
    }
    0
  }


  def runStatus(asJson: Boolean = false): Int = {
    Try { Await.result(sal.getStatus, TIMEOUT) } match {
      case Success(status) => printStatus(status, asJson)
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  def runGetDataSetInfo(datasetId: Either[Int, UUID], asJson: Boolean = false): Int = {
    Try { Await.result(sal.getDataSetByAny(datasetId), TIMEOUT) } match {
      case Success(ds) => printDataSetInfo(ds, asJson)
      case Failure(err) => errorExit(s"Could not retrieve existing dataset record: ${err}")
    }
  }

  def runGetDataSets(dsType: String, maxItems: Int, asJson: Boolean = false): Int = {
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
        if (asJson) {
          var k = 1
          for (ds <- records) {
            // XXX this is annoying - the records get interpreted as
            // Seq[ServiceDataSetMetaData], which can't be unmarshalled
            var sep = if (k < records.size) "," else ""
            dsType match {
              case "subreads" => println(ds.asInstanceOf[SubreadServiceDataSet].toJson.prettyPrint + sep)
              case "hdfsubreads" => println(ds.asInstanceOf[HdfSubreadServiceDataSet].toJson.prettyPrint + sep)
              case "barcodes" => println(ds.asInstanceOf[BarcodeServiceDataSet].toJson.prettyPrint + sep)
              case "references" => println(ds.asInstanceOf[ReferenceServiceDataSet].toJson.prettyPrint + sep)
            }
            k += 1
          }
        } else {
          var k = 0
          val table = for (ds <- records.reverse if k < maxItems) yield {
            k += 1
            Seq(ds.id.toString, ds.uuid.toString, ds.name, ds.path)
          }
          printTable(table, Seq("ID", "UUID", "Name", "Path"))
        }
        0
      }
      case Failure(err) => {
        errorExit(s"Error: ${err.getMessage}")
      }
    }
  }

  def runGetJobInfo(jobId: Either[Int, UUID], asJson: Boolean = false): Int = {
    Try { Await.result(sal.getJobByAny(jobId), TIMEOUT) } match {
      case Success(job) => printJobInfo(job, asJson)
      case Failure(err) => errorExit(s"Could not retrieve job record: ${err}")
    }
  }

  def runGetJobs(maxItems: Int, asJson: Boolean = false): Int = {
    Try { Await.result(sal.getAnalysisJobs, TIMEOUT) } match {
      case Success(engineJobs) => {
        if (asJson) println(engineJobs.toJson.prettyPrint) else {
          var k = 0
          val table = for (job <- engineJobs.reverse if k < maxItems) yield {
            k += 1
            Seq(job.id.toString, job.state.toString, job.name, job.uuid.toString)
          }
          printTable(table, Seq("ID", "State", "Name", "UUID"))
        }
        0
      }
      case Failure(err) => {
        errorExit(s"Could not retrieve jobs: ${err.getMessage}")
      }
    }
  }

  protected def waitForJob(jobId: UUID): Int = {
    println(s"waiting for job ${jobId} to complete...")
    Try { sal.pollForJob(jobId, maxTime) } match {
      case Success(msg) => runGetJobInfo(Right(jobId))
      case Failure(err) => {
        runGetJobInfo(Right(jobId))
        errorExit(err.getMessage)
      }
    }
  }

  def runImportFasta(
      path: String, name: String,
      organism: String,
      ploidy: String): Int = {
    var nameFinal = name
    if (name == "") nameFinal = "unknown" // this really shouldn't be optional
    PacBioFastaValidator(Paths.get(path)) match {
      case Some(x) => errorExit(s"Fasta validation failed: ${x.msg}")
      case _ => Try {
        Await.result(sal.importFasta(path, nameFinal, organism, ploidy), TIMEOUT)
      } match {
        case Success(job: EngineJob) => {
          println(job)
          waitForJob(job.uuid) match {
            case 0 => {
              Try {
                Await.result(sal.getImportFastaJobDataStore(job.id), TIMEOUT)
              } match {
                case Success(dataStoreFiles) => {
                  for (dsFile <- dataStoreFiles) {
                    if (dsFile.fileTypeId == "PacBio.DataSet.ReferenceSet") {
                      return runGetDataSetInfo(Right(dsFile.uuid))
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
  }

  def runImportDataSetSafe(path: String): Int = {
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
        val dsType = dsMetaTypeFromPath(path)
        val rc = runImportDataSet(path, dsType)
        if (rc == 0) runGetDataSetInfo(Right(dsUuid)) else rc
      }
    }
  }

  def runImportDataSet(path: String, dsType: String): Int = {
    logger.info(dsType)
    Try { Await.result(sal.importDataSet(path, dsType), TIMEOUT) } match {
      case Success(jobInfo: EngineJob) => {
        println(jobInfo)
        waitForJob(jobInfo.uuid)
      }
      case Failure(err) => {
        errorExit(s"Dataset import failed: ${err}")
      }
    }
  }

  private def listDataSetFiles(f: File): Array[File] = {
    (f.listFiles.filter((fn) =>
      Try {
        dsMetaTypeFromPath(fn.getAbsolutePath)
      } match {
        case Success(dsType) => true
        case _ => false
      })
    ).toArray ++ f.listFiles.filter(_.isDirectory).flatMap(listDataSetFiles)
  }

  def runImportDataSets(f: File, nonLocal: Option[String]): Int = {
    nonLocal match {
      case Some(dsType) =>
        logger.info(s"Non-local file, importing as type ${dsType}")
        runImportDataSet(f.getAbsolutePath, dsType)
      case _ =>
        if (f.isDirectory) {
          val xmlFiles = listDataSetFiles(f)
          if (xmlFiles.size == 0) {
            errorExit(s"No valid datasets found in ${f.getAbsolutePath}")
          } else {
            println(s"Found ${xmlFiles.size} DataSet XML files")
            (for (xml <- xmlFiles) yield runImportDataSetSafe(xml.getAbsolutePath)).toList.max
          }
        } else if (f.isFile) runImportDataSetSafe(f.getAbsolutePath)
        else errorExit(s"${f.getAbsolutePath} is not readable")
    }
  }

  def runEmitAnalysisTemplate: Int = {
    val analysisOpts = {
      val ep = BoundServiceEntryPoint("eid_subread", "PacBio.DataSet.SubreadSet", Left(0))
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

  def runAnalysisPipeline(jsonPath: String, block: Boolean): Int = {
    val jsonSrc = Source.fromFile(jsonPath).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    val analysisOptions = jsonAst.convertTo[PbSmrtPipeServiceOptions]
    runAnalysisPipelineImpl(analysisOptions, block)
  }

  protected def validateEntryPoints(entryPoints: Seq[BoundServiceEntryPoint]): Int = {
    for (entryPoint <- entryPoints) {
      Try {
        Await.result(sal.getDataSetByAny(entryPoint.datasetId), TIMEOUT)
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
    0
  }

  protected def validatePipelineId(pipelineId: String): Int = {
    Try {
      Await.result(sal.getPipelineTemplateJson(pipelineId), TIMEOUT)
    } match {
      case Success(x) => printMsg(s"Found pipeline template ${pipelineId}")
      case Failure(err) => errorExit(s"Can't find pipeline template ${pipelineId}: ${err.getMessage}\nUse 'pbsmrtpipe show-templates' to display a list of available pipelines")
    }
  }

  protected def validatePipelineOptions(analysisOptions: PbSmrtPipeServiceOptions): Int = {
    max(validatePipelineId(analysisOptions.pipelineId),
        validateEntryPoints(analysisOptions.entryPoints))
  }

  protected def runAnalysisPipelineImpl(analysisOptions: PbSmrtPipeServiceOptions, block: Boolean = true, validate: Boolean = true): Int = {
    //println(analysisOptions)
    var xc = 0
    if (validate) {
      xc = validatePipelineOptions(analysisOptions)
      if (xc != 0) return errorExit("Analysis options failed validation")
    }
    Try {
      Await.result(sal.runAnalysisPipeline(analysisOptions), TIMEOUT)
    } match {
      case Success(jobInfo) => {
        println(s"Job ${jobInfo.uuid} started")
        printJobInfo(jobInfo)
        if (block) waitForJob(jobInfo.uuid) else 0
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  protected def importEntryPoint(eid: String, xmlPath: String): BoundServiceEntryPoint = {
    var dsType = dsMetaTypeFromPath(xmlPath)
    var dsUuid = dsUuidFromPath(xmlPath)
    var xc = runImportDataSetSafe(xmlPath)
    if (xc != 0) throw new Exception(s"Could not import dataset ${eid}:${xmlPath}")
    // this is stupidly inefficient
    val dsId = Try {
      Await.result(sal.getDataSetByUuid(dsUuid), TIMEOUT)
    } match {
      case Success(ds) => ds.id
      case Failure(err) => throw new Exception(err.getMessage)
    }
    BoundServiceEntryPoint(eid, dsType, Left(dsId))
  }

  protected def importEntryPointAutomatic(entryPoint: String): BoundServiceEntryPoint = {
    val epFields = entryPoint.split(':')
    if (epFields.length == 2) importEntryPoint(epFields(0), epFields(1))
    else if (epFields.length == 1) {
      val xmlPath = epFields(0)
      val dsType = dsMetaTypeFromPath(xmlPath)
      val eid = entryPointsLookup(dsType)
      importEntryPoint(eid, xmlPath)
    } else throw new Exception(s"Can't interpret argument ${entryPoint}")
  }

  protected def getPipelinePresets(presetXml: File): PipelineTemplatePreset = {
    if (presetXml != null) {
      val presetPath = Paths.get(presetXml.getAbsolutePath)
      PipelineTemplatePresetLoader.loadFrom(presetPath)
    } else defaultPresets
  }

  protected object OptionTypes {
    val FLOAT = "pbsmrtpipe.option_types.float"
    val INTEGER = "pbsmrtpipe.option_types.integer"
    val STRING = "pbsmrtpipe.option_types.string"
    val BOOLEAN = "pbsmrtpipe.option_types.boolean"
  }

  // XXX there is a bit of a disconnect between how preset.xml is handled and
  // how options are actually passed to services, so we need to convert them
  // here
  protected def getPipelineServiceOptions(jobTitle: String, pipelineId: String,
      entryPoints: Seq[BoundServiceEntryPoint],
      presets: PipelineTemplatePreset): PbSmrtPipeServiceOptions = {
    Try {
      Await.result(sal.getPipelineTemplateJson(pipelineId), TIMEOUT)
    } match {
      case Success(pipelineJson) => {
        val presetOptionsLookup = (for (presetOpt <- presets.taskOptions) yield {
          (presetOpt.id, presetOpt.value.toString)
        }).toMap
        // FIXME unmarshalling is broken, so this is a little hacky
        val jtaskOptions = pipelineJson.parseJson.asJsObject.getFields("taskOptions")(0).asJsObject.getFields("properties")(0).asJsObject.fields
        val taskOptions: Seq[ServiceTaskOptionBase] = (for ((id,templateOpt) <- jtaskOptions) yield {
          val template = templateOpt.asJsObject.fields
          val optionValue = presetOptionsLookup.getOrElse(id,
            template("default") match {
              case JsString(x) => x
              case JsNumber(x) => x.toString
              case JsBoolean(x) => x.toString
            })
          template("optionTypeId").asInstanceOf[JsString].value match {
            case OptionTypes.STRING => ServiceTaskStrOption(id, optionValue,
                                                            OptionTypes.STRING)
            case OptionTypes.INTEGER => ServiceTaskIntOption(id, optionValue.toInt, OptionTypes.INTEGER)
            case OptionTypes.FLOAT => ServiceTaskDoubleOption(id, optionValue.toDouble, OptionTypes.FLOAT)
            case OptionTypes.BOOLEAN => ServiceTaskBooleanOption(id, optionValue.toBoolean, OptionTypes.BOOLEAN)
          }
        }).toList
        val workflowOptions = Seq[ServiceTaskOptionBase]()
        PbSmrtPipeServiceOptions(jobTitle, pipelineId, entryPoints, taskOptions,
                                 workflowOptions)
      }
      case Failure(err) => throw new Exception(s"Failed to decipher pipeline options: ${err.getMessage}")
    }
  }

  def runPipeline(pipelineId: String, entryPoints: Seq[String], jobTitle: String,
                  presetXml: File = null, block: Boolean = true, validate: Boolean = true): Int = {
    if (entryPoints.length == 0) return errorExit("At least one entry point is required")
    var pipelineIdFull: String = pipelineId
    val idFields = pipelineIdFull.split('.')
    if (idFields.size != 3) pipelineIdFull = s"pbsmrtpipe.pipelines.${pipelineId}"
    println(s"pipeline ID: ${pipelineIdFull}")
    if (validatePipelineId(pipelineIdFull) != 0) return errorExit("Aborting")
    var jobTitleTmp = jobTitle
    if (jobTitle.length == 0) jobTitleTmp = s"pbservice-${pipelineIdFull}"
    Try {
      for (ep <- entryPoints) yield importEntryPointAutomatic(ep)
    } match {
      case Success(eps) => {
        Try {
          getPipelineServiceOptions(jobTitleTmp, pipelineIdFull, eps,
                                    getPipelinePresets(presetXml))
        } match {
          case Success(pipelineOptions) => runAnalysisPipelineImpl(
            pipelineOptions, block=block, validate=false)
          case Failure(err) => errorExit(err.getMessage)
        }
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }
}

object PbService {
  def apply (c: PbServiceParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    // FIXME we need some kind of hostname validation here - supposedly URL
    // creation includes validation, but it wasn't failing on extra 'http://'
    val host = c.host.replaceFirst("http://", "")
    val url = new URL(s"http://${host}:${c.port}")
    val sal = new AnalysisServiceAccessLayer(url)(actorSystem)
    val ps = new PbService(sal, c.maxTime)
    try {
      c.mode match {
        case Modes.STATUS => ps.runStatus(c.asJson)
        case Modes.IMPORT_DS => ps.runImportDataSets(c.path, c.nonLocal)
        case Modes.IMPORT_FASTA => ps.runImportFasta(c.path.getAbsolutePath,
                                                     c.name, c.organism, c.ploidy)
        case Modes.ANALYSIS => ps.runAnalysisPipeline(c.path.getAbsolutePath,
                                                      c.block)
        case Modes.TEMPLATE => ps.runEmitAnalysisTemplate
        case Modes.PIPELINE => ps.runPipeline(c.pipelineId, c.entryPoints,
                                              c.jobTitle, c.presetXml, c.block)
        case Modes.JOB => ps.runGetJobInfo(c.jobId, c.asJson)
        case Modes.JOBS => ps.runGetJobs(c.maxItems, c.asJson)
        case Modes.DATASET => ps.runGetDataSetInfo(c.datasetId, c.asJson)
        case Modes.DATASETS => ps.runGetDataSets(c.datasetType, c.maxItems, c.asJson)
        case _ => {
          println("Unsupported action")
          1
        }
      }
    } finally {
      actorSystem.shutdown()
    }
  }
}

object PbServiceApp extends App {
  def run(args: Seq[String]) = {
    val xc = PbServiceParser.parser.parse(args.toSeq, PbServiceParser.defaults) match {
      case Some(config) => PbService(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
