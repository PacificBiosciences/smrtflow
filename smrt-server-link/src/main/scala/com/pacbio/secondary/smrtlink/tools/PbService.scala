package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.net.URL
import java.nio.file.{Path, Paths, Files}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.converters._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.pipelines._
import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.pacbio.secondary.smrtlink.client._
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser
import spray.json._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.language.postfixOps
import scala.math._
import scala.util.control.NonFatal
import scala.util.{Failure, Properties, Success, Try}


object Modes {
  sealed trait Mode {
    val name: String
  }
  case object STATUS extends Mode {val name = "status"}
  case object IMPORT_DS extends Mode {val name = "import-dataset"}
  case object IMPORT_FASTA extends Mode {val name = "import-fasta"}
  case object IMPORT_BARCODES extends Mode {val name = "import-barcodes"}
  case object ANALYSIS extends Mode {val name = "run-analysis"}
  case object TEMPLATE extends Mode {val name = "emit-analysis-template"}
  case object PIPELINE extends Mode {val name = "run-pipeline"}
  case object SHOW_PIPELINES extends Mode {val name = "show-pipelines"}
  case object IMPORT_MOVIE extends Mode {val name = "import-rs-movie"}
  case object JOB extends Mode {val name = "get-job"}
  case object JOBS extends Mode {val name = "get-jobs"}
  case object TERMINATE_JOB extends Mode { val name = "terminate-job"} // This currently ONLY supports Analysis Jobs
  case object DELETE_JOB extends Mode { val name = "delete-job" } // also only analysis jobs
  case object DATASET extends Mode {val name = "get-dataset"}
  case object DATASETS extends Mode {val name = "get-datasets"}
  case object DELETE_DATASET extends Mode {val name = "delete-dataset"}
  case object CREATE_PROJECT extends Mode {val name = "create-project"}
  case object MANIFESTS extends Mode {val name = "get-manifests"}
  case object MANIFEST extends Mode {val name = "get-manifest"}
  case object BUNDLES extends Mode {val name = "get-bundles"}
  case object TS_STATUS extends Mode {val name = "ts-status"}
  case object TS_JOB extends Mode {val name = "ts-failed-job"}
  case object UNKNOWN extends Mode {val name = "unknown"}
}

object PbServiceParser extends CommandLineToolVersion{
  import CommonModelImplicits._
  import CommonModels._

  val VERSION = "0.1.1"
  var TOOL_ID = "pbscala.tools.pbservice"

  private def getSizeMb(fileObj: File): Double = {
    fileObj.length / 1024.0 / 1024.0
  }

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  def showVersion: Unit = showToolVersion(TOOL_ID, VERSION)

  // is there a cleaner way to do this?
  private def entityIdOrUuid(entityId: String): IdAble = {
    try {
      IntIdAble(entityId.toInt)
    } catch {
      case e: Exception => {
        try {
          UUIDIdAble(UUID.fromString(entityId))
        } catch {
          case e: Exception => 0
        }
      }
    }
  }

  private def getToken(token: String): String = {
    if (Paths.get(token).toFile.isFile) {
      Source.fromFile(token).getLines.take(1).toList.head
    } else token
  }

  case class CustomConfig(
      mode: Modes.Mode = Modes.UNKNOWN,
      host: String,
      port: Int,
      block: Boolean = false,
      command: CustomConfig => Unit = showDefaults,
      datasetId: IdAble = 0,
      jobId: IdAble = 0,
      path: Path = null,
      name: String = "",
      organism: String = "",
      ploidy: String = "",
      maxItems: Int = 25,
      datasetType: String = "subreads",
      jobType: String = "pbsmrtpipe",
      jobState: Option[String] = None,
      nonLocal: Option[DataSetMetaTypes.DataSetMetaType] = None,
      asJson: Boolean = false,
      dumpJobSettings: Boolean = false,
      pipelineId: String = "",
      jobTitle: String = "",
      entryPoints: Seq[String] = Seq(),
      presetXml: Option[Path] = None,
      taskOptions: Option[Map[String,String]] = None,
      maxTime: FiniteDuration = 30.minutes, // This probably needs to be tuned on a per subparser.
      project: Option[String] = None,
      description: String = "",
      authToken: Option[String] = Properties.envOrNone("PB_SERVICE_AUTH_TOKEN"),
      manifestId: String = "smrtlink",
      showReports: Boolean = false,
      searchName: Option[String] = None,
      searchPath: Option[String] = None,
      force: Boolean = false,
      user: String = System.getProperty("user.name"),
      comment: String = "Sent via pbservice"
  ) extends LoggerConfig


  lazy val defaultHost: String = Properties.envOrElse("PB_SERVICE_HOST", "localhost")
  lazy val defaultPort: Int = Properties.envOrElse("PB_SERVICE_PORT", "8070").toInt
  lazy val defaults = CustomConfig(null, defaultHost, defaultPort)

  lazy val parser = new OptionParser[CustomConfig]("pbservice") {

    private def validateId(entityId: String, entityType: String): Either[String, Unit] = {
      entityIdOrUuid(entityId) match {
        case IntIdAble(x) => if (x > 0) success else failure(s"${entityType} ID must be a positive integer or a UUID string")
        case UUIDIdAble(x) => success
      }
    }

    private def validateJobType(jobType: String): Either[String,Unit] = {
      JobTypeIds.fromString(jobType)
          .map(_ => success)
          .getOrElse(failure(s"Unrecognized job type $jobType"))
    }

    private def validateDataSetMetaType(dsType: String): Either[String, Unit] = {
      val errorMsg = s"Invalid DataSet type '$dsType'"
      DataSetMetaTypes.toDataSetType(dsType)
          .map(_ => success)
          .getOrElse(failure(errorMsg))
    }

    head("PacBio SMRTLink Services Client", VERSION)

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text s"Hostname of smrtlink server (default: $defaultHost).  Override the default with env PB_SERVICE_HOST."

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text s"Services port on smrtlink server (default: $defaultPort).  Override default with env PB_SERVICE_PORT."

    // FIXME(nechols)(2016-09-21) disabled due to WSO2, will revisit later
    /*opt[String]("token") action { (t, c) =>
      c.copy(authToken = Some(getToken(t)))
    } text "Authentication token (required for project services)"*/

    opt[Unit]("json") action { (_, c) =>
      c.copy(asJson = true)
    } text "Display output as JSON"

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
        c.copy(path = p.toPath)
      } text "DataSet XML path (or directory containing datasets)",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t.seconds)
      } text s"Maximum time to poll for running job status in seconds (Default ${defaults.maxTime})",
      opt[String]("non-local").action { (t, c) =>
        c.copy(nonLocal = DataSetMetaTypes.toDataSetType(t))
      }.validate(validateDataSetMetaType)
          .text("Import non-local dataset with specified type (e.g. PacBio.DataSet.SubreadSet)") /*,
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with this dataset"*/
    ) text "Import DataSet XML"

    cmd(Modes.IMPORT_FASTA.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_FASTA)
    } children(
      arg[File]("fasta-path") required() action { (p, c) =>
        c.copy(path = p.toPath)
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
        c.copy(maxTime = t.seconds)
      } text s"Maximum time to poll for running job status in seconds (Default ${defaults.maxTime})" /*,
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with this reference" */
    ) text "Import Reference FASTA"

    cmd(Modes.IMPORT_BARCODES.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_BARCODES)
    } children(
      arg[File]("fasta-path") required() action { (p, c) =>
        c.copy(path = p.toPath)
      } text "FASTA path",
      arg[String]("name") required() action { (name, c) =>
        c.copy(name = name)
      } text "Name of BarcodeSet" /*,
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with these barcodes" */
    ) text "Import Barcodes FASTA"

    cmd(Modes.IMPORT_MOVIE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_MOVIE)
    } children(
      arg[File]("metadata-xml-path") required() action { (p, c) =>
        c.copy(path = p.toPath)
      } text "Path to RS II movie metadata XML file (or directory)",
      opt[String]("name") action { (name, c) =>
        c.copy(name = name)
      } text "Name of imported HdfSubreadSet" /*,
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with this dataset"*/
    ) text "Import RS II movie metadata XML legacy format as HdfSubreadSet"

    cmd(Modes.ANALYSIS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.ANALYSIS)
    } children(
      arg[File]("json-file") required() action { (p, c) =>
        c.copy(path = p.toPath)
      } text "JSON config file", // TODO validate json format
      opt[Unit]("block") action { (_, c) =>
        c.copy(block = true)
      } text "Block until job completes",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t.seconds)
      } text s"Maximum time (in seconds) to poll for running job status (Default ${defaults.maxTime})"
    ) text "Run a pbsmrtpipe analysis pipeline from a JSON config file"

    cmd(Modes.TERMINATE_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TERMINATE_JOB)
    } children(
        arg[Int]("job-id") required() action { (p, c) =>
          c.copy(jobId = p)
        } text "SMRT Link Analysis Job Id"
        ) text "Terminate a SMRT Link Analysis Job By Int Id in the RUNNING state"

    cmd(Modes.DELETE_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DELETE_JOB)
    } children(
      arg[String]("job-id") required() action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } validate { i => validateId(i, "Job") } text "Job ID",
      opt[Unit]("force") action { (_, c) =>
        c.copy(force = true)
      } text "Force job delete even if it is still running or has active child jobs (NOT RECOMMENDED)"
    ) text "Delete a pbsmrtpipe job, including all output files"

    cmd(Modes.TEMPLATE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TEMPLATE)
    } children(
    ) text "Emit an analysis.json template to stdout that can be run using 'run-analysis'"

    cmd(Modes.SHOW_PIPELINES.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.SHOW_PIPELINES)
    } text "Display a list of available pbsmrtpipe pipelines"

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
        c.copy(presetXml = Some(x.toPath))
      } text "XML file specifying pbsmrtpipe options",
      opt[String]("job-title") action { (t, c) =>
        c.copy(jobTitle = t)
      } text "Job title (will be displayed in UI)",
      opt[Unit]("block") action { (_, c) =>
        c.copy(block = true)
      } text "Block until job completes",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t.seconds)
      } text s"Maximum time (in seconds) to poll for running job status (Default ${defaults.maxTime})",
      opt[Map[String,String]]("task-options").valueName("k1=v1,k2=v2...").action{ (x, c) =>
        c.copy(taskOptions = Some(x))
      }.text("Pipeline task options as comma-separated option_id=value list")
    ) text "Run a pbsmrtpipe pipeline by name on the server"

    cmd(Modes.JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.JOB)
    } children(
      arg[String]("job-id") required() action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } validate { i => validateId(i, "Job") } text "Job ID",
      opt[Unit]("show-settings") action { (_, c) =>
        c.copy(dumpJobSettings = true)
      } text "Print JSON settings for job, suitable for input to 'pbservice run-analysis'",
      opt[Unit]("show-reports") action { (_, c) =>
        c.copy(showReports = true)
      } text "Display job report attributes"
    ) text "Show job details"

    cmd(Modes.JOBS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.JOBS)
    } children(
      opt[Int]('m', "max-items") action { (m, c) =>
        c.copy(maxItems = m)
      } text "Max number of jobs to show",
      opt[String]('t', "job-type") action { (t, c) =>
        c.copy(jobType = t)
      } validate {
        t => validateJobType(t)
      } text "Only retrieve jobs of specified type",
      opt[String]("job-state") action { (s, c) =>
        c.copy(jobState = Some(s))
      } text "Only display jobs in specified state"
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
      } text "Max number of Datasets to show",
      opt[String]("search-name") action { (n, c) =>
        c.copy(searchName = Some(n))
      } text "Search for datasets whose 'name' field matches the specified string",
      opt[String]("search-path") action { (p, c) =>
        c.copy(searchPath = Some(p))
      } text "Search for datasets whose 'path' field matches the specified string"
    )

    cmd(Modes.DELETE_DATASET.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DELETE_DATASET)
    } children(
      arg[String]("dataset-id") required() action { (i, c) =>
        c.copy(datasetId = entityIdOrUuid(i))
      } validate { i => validateId(i, "Dataset") } text "Dataset ID"
    ) text "Soft-delete of a dataset (won't remove files)"

    cmd(Modes.MANIFESTS.name) action {(_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.MANIFESTS)
    } text "Get a List of SMRT Link PacBio Component Versions"

    cmd(Modes.MANIFEST.name) action {(_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.MANIFEST)
    } children(
        opt[String]('i', "manifest-id") action { (t, c) =>
          c.copy(manifestId = t)
        } text s"Manifest By Id (Default: ${defaults.manifestId})"
        ) text "Get PacBio Component Manifest version by Id."

    cmd(Modes.BUNDLES.name) action {(_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.BUNDLES)
    } text "Get a List of PacBio Data Bundles registered to SMRT Link"

    // FIXME(nechols)(2016-09-21) disabled due to WSO2, will revisit later
    /*cmd(Modes.CREATE_PROJECT.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.CREATE_PROJECT)
    } children(
      arg[String]("name") required() action { (n, c) =>
        c.copy(name = n)
      } text "Project name",
      arg[String]("description") required() action { (d, c) =>
        c.copy(description = d)
      } text "Project description"
    ) text "Start a new project"*/

    cmd(Modes.TS_STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TS_STATUS)
    } children(
      opt[String]("user") action { (u, c) =>
        c.copy(user = u)
      } text s"User name to send (default: ${defaults.user})",
      opt[String]("comment") action { (s, c) =>
        c.copy(comment = s)
      } text s"Comments to include (default: ${defaults.comment})"
    ) text "Send system status report to PacBio Tech Support"

    cmd(Modes.TS_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TS_JOB)
    } children(
      arg[String]("job-id") required() action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } text "ID of job whose details should be sent to tech support",
      opt[String]("user") action { (u, c) =>
        c.copy(user = u)
      } text s"User name to send (default: ${defaults.user})",
      opt[String]("comment") action { (s, c) =>
        c.copy(comment = s)
      } text s"Comments to include (default: ${defaults.comment})"
    ) text "Send failed job information to PacBio Tech Support"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    // Don't show the help if validation error
    override def showUsageOnError = false
  }
}


// TODO consolidate Try behavior
class PbService (val sal: SmrtLinkServiceAccessLayer,
                 val maxTime: FiniteDuration) extends LazyLogging with ClientUtils with DaoFutureUtils{
  import CommonModelImplicits._
  import CommonModels._
  import ServicesClientJsonProtocol._

  // the is the default for timeout for common tasks
  protected val TIMEOUT = 30 seconds
  private lazy val entryPointsLookup = Map(
    "PacBio.DataSet.SubreadSet" -> "eid_subread",
    "PacBio.DataSet.ReferenceSet" -> "eid_ref_dataset",
    "PacBio.DataSet.BarcodeSet" -> "eid_barcode",
    "PacBio.DataSet.HdfSubreadSet" -> "eid_hdfsubread",
    "PacBio.DataSet.ConsensusReadSet" -> "eid_ccs",
    "PacBio.DataSet.AlignmentSet" -> "eid_alignment")
  private lazy val defaultPresets = PipelineTemplatePreset("default", "any",
    Seq[ServiceTaskOptionBase](),
    Seq[ServiceTaskOptionBase]())
  private lazy val rsMovieName = """m([0-9]{6})_([0-9a-z]{5,})_([0-9a-z]{5,})_c([0-9]{16,})_(\w\d)_(\w\d)""".r

  private def matchRsMovieName(file: File): Boolean =
    rsMovieName.findPrefixMatchOf(file.getName).isDefined

  // FIXME this is crude
  private def printAndExit(msg: String, exitCode: Int): Int = {
    println(msg)
    exitCode
  }

  protected def errorExit(msg: String, exitCode: Int = 1) = {
    System.err.println(msg)
    exitCode
  }

  protected def printMsg(msg: String) = printAndExit(msg, 0)

  protected def showNumRecords(label: String, numPad: Int, fn: () => Future[Int]): Unit = {
    Try { Await.result(fn(), TIMEOUT) } match {
      case Success(nrecords) => println(s"${label.padTo(numPad, ' ')} $nrecords")
      case Failure(err) => println(s"ERROR: couldn't retrieve $label")
    }
  }

  protected def runAndSummary[T](fx: Try[T], summary:(T => String)): Int = {
    fx match {
      case Success(result) => printMsg(summary(result))
      case Failure(ex) => errorExit(ex.getMessage, 1)
    }
  }

  protected def runAndBlock[T](fx: => Future[T],
                               summary:(T => String),
                               timeout: FiniteDuration): Int = {
    runAndSummary(Try(Await.result[T](fx, timeout)), summary)
  }

  def statusSummary(status: ServiceStatus): String = {
    val headers = Seq("ID", "UUID", "Version", "Message")
    val table = Seq(Seq(status.id.toString, status.uuid.toString, status.version, status.message))
    printTable(table, headers)
    ""
  }

  protected def isCompatibleVersion(): Boolean = {
    val fx = for {
      status <- sal.getStatus
      _ <- isVersionGteSystemVersion(status)
    } yield true

    Try(Await.result(fx, TIMEOUT)).getOrElse(false)
  }

  def systemDataSetSummary(): Future[String] = {
    for {
      numSubreadSets <- sal.getSubreadSets.map(_.length)
      numHdfSubreadSets <- sal.getHdfSubreadSets.map(_.length)
      numReferenceSets <- sal.getReferenceSets.map(_.length)
      numGmapReferenceSets <- sal.getGmapReferenceSets.map(_.length)
      numAlignmenSets <- sal.getAlignmentSets.map(_.length)
      numBarcodeSets <- sal.getBarcodeSets.map(_.length)
      numConsensusReadSets <- sal.getConsensusReadSets.map(_.length)
      numConsensusAlignmentSets <- sal.getConsensusAlignmentSets.map(_.length)
      numContigSets <- sal.getContigSets.map(_.length)
    } yield
      s"""
        |DataSet Summary (active datasets) :
        |
        |SubreadSets            : $numSubreadSets
        |HdfSubreadSets         : $numHdfSubreadSets
        |ReferenceSets          : $numReferenceSets
        |GmapReferenceSets      : $numGmapReferenceSets
        |BarcodeSets            : $numBarcodeSets
        |AlignmentSets          : $numAlignmenSets
        |ConsensusAlignmentSets : $numConsensusAlignmentSets
        |ConsensusReadSets      : $numConsensusReadSets
        |ContigSets             : $numContigSets
      """.stripMargin
  }

  def systemJobSummary(): Future[String] = {
    for {
      numImportJobs <- sal.getImportJobs.map(_.length)
      numMergeJobs <- sal.getMergeJobs.map(_.length)
      numAnalysisJobs <- sal.getAnalysisJobs.map(_.length)
      numConvertFastaJobs <- sal.getFastaConvertJobs.map(_.length)
      numConvertFastaBarcodeJobs <- sal.getBarcodeConvertJobs.map(_.length)
      numTsSystemBundleJobs <- Future.successful(0) // These need to be added
      numTsFailedJobBundleJob <- Future.successful(0)
      numDbBackUpJobs <- Future.successful(0)
    } yield
      s"""
        |System Job Summary by job type:
        |
        |Import DataSet                    : $numImportJobs
        |Merge DataSet                     : $numMergeJobs
        |Analysis                          : $numAnalysisJobs
        |Convert Fasta to ReferenceSet     : $numConvertFastaJobs
        |Convert Fasta to BarcodeSet       : $numConvertFastaBarcodeJobs
      """.stripMargin
  }

  protected def printStatus(status: ServiceStatus, asJson: Boolean = false): Int = {
    if (asJson) {
      println(status.toJson.prettyPrint)
    } else{
      statusSummary(status)
      println("")
      println("DataSet Summary:")
      // To have the clear summary of the output
      val numPad = 30
      val showRecords = showNumRecords(_: String, numPad, _:() => Future[Int])

      showRecords("SubreadSets", () => sal.getSubreadSets.map(_.length))
      showRecords("HdfSubreadSets", () => sal.getHdfSubreadSets.map(_.length))
      showRecords("ReferenceSets", () => sal.getReferenceSets.map(_.length))
      showRecords("BarcodeSets", () => sal.getBarcodeSets.map(_.length))
      showRecords("AlignmentSets", () => sal.getAlignmentSets.map(_.length))
      showRecords("ConsensusReadSets", () => sal.getConsensusReadSets.map(_.length))
      showRecords("ConsensusAlignmentSets", () => sal.getConsensusAlignmentSets.map(_.length))
      showRecords("ContigSets", () => sal.getContigSets.map(_.length))
      showRecords("GmapReferenceSets", () => sal.getGmapReferenceSets.map(_.length))
      println("\nSMRT Link Job Summary:")
      showRecords("import-dataset Jobs", () => sal.getImportJobs.map(_.length))
      showRecords("merge-dataset Jobs", () => sal.getMergeJobs.map(_.length))
      showRecords("convert-fasta-reference Jobs", () => sal.getFastaConvertJobs.map(_.length))
      showRecords("pbsmrtpipe Jobs", () => sal.getAnalysisJobs.map(_.length))
    }
    0
  }


  /**
    * Util to get the Pbservice Client and Server compatibility status
    *
    * @param status Remote SMRT Link Server status
    * @return
    */
  def compatibilitySummary(status: ServiceStatus): Future[String] = {
    def msg(sx: String) =
      s"Pbservice ${Constants.SMRTFLOW_VERSION} $sx compatible with Server ${status.version}"

    val fx = isVersionGteSystemVersion(status).map(_ => msg("IS"))

    fx.recover { case NonFatal(_) => Future.successful(msg("IS NOT"))}

    fx
  }


  /**
    * Core Summary for the Server Status
    *
    * If asJson is provided, the system summary is no longer relevent and will
    * be skipped.
    *
    * @param asJson to emit the System Status as JSON.
    * @return
    */
  def exeStatus(asJson: Boolean = false): Future[String] = {

    // This isn't quite correct, but statusSummary is doing the printing
    def statusFullSummary(status: ServiceStatus): Future[String] = {
      for {
        statusSummaryMsg <- Future.successful(status).map(status => statusSummary(status))
        compatSummaryMsg <- compatibilitySummary(status)
        dataSetSummaryMsg  <- systemDataSetSummary()
        jobSummaryMsg <- systemJobSummary()
      } yield s"$statusSummaryMsg\n$compatSummaryMsg\n$dataSetSummaryMsg\n$jobSummaryMsg"
    }

    def statusJsonSummary(status: ServiceStatus): Future[String] =
      Future.successful(status.toJson.prettyPrint.toString)

    val summary: (ServiceStatus => Future[String]) = if (asJson) statusJsonSummary else statusFullSummary
    sal.getStatus.flatMap(summary)
  }

  def runStatus(asJson: Boolean = false): Int = {
    def printer(sx: ServiceStatus): String = {
      printStatus(sx, asJson)
      ""
    }
    runAndBlock[ServiceStatus](sal.getStatus, printer, TIMEOUT)
  }

  def getDataSet(datasetId: IdAble): Try[DataSetMetaDataSet] = Try {
    Await.result(sal.getDataSet(datasetId), TIMEOUT)
  }

  def runGetDataSetInfo(datasetId: IdAble, asJson: Boolean = false): Int = {
    getDataSet(datasetId) match {
      case Success(ds) => printDataSetInfo(ds, asJson)
      case Failure(err) => errorExit(s"Could not retrieve existing dataset record: $err")
    }
  }

  def runGetDataSets(dsType: String,
                     maxItems: Int,
                     asJson: Boolean = false,
                     searchName: Option[String] = None,
                     searchPath: Option[String] = None): Int = {
    def isMatching(ds: ServiceDataSetMetadata): Boolean = {
      val qName = searchName.map(n => ds.name contains n).getOrElse(true)
      val qPath = searchPath.map(p => ds.path contains p).getOrElse(true)
      qName && qPath
    }
    Try {
      dsType match {
        case "subreads" => Await.result(sal.getSubreadSets, TIMEOUT)
        case "hdfsubreads" => Await.result(sal.getHdfSubreadSets, TIMEOUT)
        case "barcodes" => Await.result(sal.getBarcodeSets, TIMEOUT)
        case "references" => Await.result(sal.getReferenceSets, TIMEOUT)
        case "gmapreferences" => Await.result(sal.getGmapReferenceSets, TIMEOUT)
        case "contigs" => Await.result(sal.getContigSets, TIMEOUT)
        case "ccsalignments" => Await.result(sal.getConsensusAlignmentSets, TIMEOUT)
        case "ccsreads" => Await.result(sal.getConsensusReadSets, TIMEOUT)
        //case _ => throw Exception("Not a valid dataset type")
      }
    } match {
      case Success(records) => {
        if (asJson) {
          var k = 1
          for (ds <- records.filter(r => isMatching(r))) {
            // XXX this is annoying - the records get interpreted as
            // Seq[ServiceDataSetMetaData], which can't be unmarshalled
            val sep = if (k < records.size) "," else ""
            dsType match {
              case "subreads" => println(ds.asInstanceOf[SubreadServiceDataSet].toJson.prettyPrint + sep)
              case "hdfsubreads" => println(ds.asInstanceOf[HdfSubreadServiceDataSet].toJson.prettyPrint + sep)
              case "barcodes" => println(ds.asInstanceOf[BarcodeServiceDataSet].toJson.prettyPrint + sep)
              case "references" => println(ds.asInstanceOf[ReferenceServiceDataSet].toJson.prettyPrint + sep)
              case "gmapreferences" => println(ds.asInstanceOf[GmapReferenceServiceDataSet].toJson.prettyPrint + sep)
              case "ccsreads" => println(ds.asInstanceOf[ConsensusReadServiceDataSet].toJson.prettyPrint + sep)
              case "ccsalignments" => println(ds.asInstanceOf[ConsensusAlignmentServiceDataSet].toJson.prettyPrint + sep)
              case "contigs" => println(ds.asInstanceOf[ContigServiceDataSet].toJson.prettyPrint + sep)
            }
            k += 1
          }
        } else {
          var k = 0
          val table = for {
            ds <- records.filter(r => isMatching(r)).reverse if k < maxItems
          } yield {
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

  def runGetJobInfo(jobId: IdAble,
                    asJson: Boolean = false,
                    dumpJobSettings: Boolean = false,
                    showReports: Boolean = false): Int = {
    Try { Await.result(sal.getJob(jobId), TIMEOUT) } match {
      case Success(job) =>
        var rc = printJobInfo(job, asJson, dumpJobSettings)
        if (showReports && (rc == 0)) {
          rc = Try {
            Await.result(sal.getAnalysisJobReports(job.uuid), TIMEOUT)
          } match {
            case Success(rpts) => rpts.map { dsr =>
              Try {
                Await.result(sal.getAnalysisJobReport(job.uuid, dsr.dataStoreFile.uuid), TIMEOUT)
              } match {
                case Success(rpt) => showReportAttributes(rpt)
                case Failure(err) => errorExit(s"Couldn't retrieve report ${dsr.dataStoreFile.uuid} for job ${job.uuid}: $err")
              }
            }.reduceLeft(_ max _)
            case Failure(err) => errorExit(s"Could not retrieve reports: $err")
          }
        }
        rc
      case Failure(err) => errorExit(s"Could not retrieve job record: $err")
    }
  }

  def jobsSummary(maxItems: Int,
                  asJson: Boolean,
                  engineJobs: Seq[EngineJob],
                  jobState: Option[String] = None): String = {
    if (asJson) {
      println(engineJobs.toJson.prettyPrint)
      ""
    } else {
      val table = engineJobs.sortBy(_.id).reverse.filter( job =>
        jobState.map(_ == job.state.toString).getOrElse(true)
      ).take(maxItems).map( job =>
        Seq(job.id.toString, job.state.toString, job.name, job.uuid.toString, job.createdBy.getOrElse("").toString))
      printTable(table, Seq("ID", "State", "Name", "UUID", "CreatedBy"))
      ""
    }
  }

  def runGetJobs(maxItems: Int,
                 asJson: Boolean = false,
                 jobType: String = "pbsmrtpipe",
                 jobState: Option[String] = None): Int = {
    def printer(jobs: Seq[EngineJob]) = jobsSummary(maxItems, asJson, jobs, jobState)

    runAndBlock[Seq[EngineJob]](sal.getJobsByType(jobType), printer, TIMEOUT)
  }

  protected def waitForJob(jobId: IdAble): Int = {
    logger.info(s"waiting for job ${jobId.toIdString} to complete...")
    sal.pollForJob(jobId, Some(maxTime)) match {
      case Success(msg) => runGetJobInfo(jobId)
      case Failure(err) => {
        runGetJobInfo(jobId)
        errorExit(err.getMessage)
      }
    }
  }

  private def importFasta(path: Path,
                          dsType: FileTypes.DataSetBaseType,
                          runJob: () => Future[EngineJob],
                          getDataStore: IdAble => Future[Seq[DataStoreServiceFile]],
                          projectName: Option[String],
                          barcodeMode: Boolean = false): Int = {
    val projectId = getProjectIdByName(projectName)
    if (projectId < 0) return errorExit("Can't continue with an invalid project.")
    val tx = for {
      contigs <- Try { PacBioFastaValidator.validate(path, barcodeMode) }
      job <- Try { Await.result(runJob(), TIMEOUT) }
      job <- sal.pollForJob(job.uuid, Some(maxTime))
      dataStoreFiles <- Try { Await.result(getDataStore(job.uuid), TIMEOUT) }
    } yield dataStoreFiles

    tx match {
      case Success(dataStoreFiles) =>
        dataStoreFiles.find(_.fileTypeId == dsType.fileTypeId) match {
          case Some(ds) =>
            runGetDataSetInfo(ds.uuid)
            if (projectId > 0) addDataSetToProject(ds.uuid, projectId) else 0
          case None => errorExit(s"Couldn't find ${dsType.dsName}")
        }
      case Failure(err) => errorExit(s"Import job error: ${err.getMessage}")
    }
  }

  def runImportFasta(path: Path,
                     name: String,
                     organism: String,
                     ploidy: String,
                     projectName: Option[String] = None): Int = {
    // this really shouldn't be optional
    val nameFinal = if (name.isEmpty) "unknown" else name
    importFasta(path,
                FileTypes.DS_REFERENCE,
                () => sal.importFasta(path, nameFinal, organism, ploidy),
                sal.getImportFastaJobDataStore,
                projectName)
  }

  def runImportBarcodes(path: Path,
                        name: String,
                        projectName: Option[String] = None): Int =
    importFasta(path,
                FileTypes.DS_BARCODE,
                () => sal.importFastaBarcodes(path, name),
                sal.getImportBarcodesJobDataStore,
                projectName,
                barcodeMode = true)

  private def importXmlRecursive(path: Path,
                                 listFilesOfType: File => Array[File],
                                 doImportOne: Path => Int,
                                 doImportMany: Path => Int): Int = {
    val f = path.toFile
    if (f.isDirectory) {
      val xmlFiles = listFilesOfType(f)
      if (xmlFiles.isEmpty) {
        errorExit(s"No valid XML files found in ${f.getAbsolutePath}")
      } else {
        println(s"Found ${xmlFiles.length} matching XML files")
        val failed: ListBuffer[String] = ListBuffer()
        xmlFiles.foreach { xmlFile =>
          println(s"Importing ${xmlFile.getAbsolutePath}...")
          val rc = doImportMany(xmlFile.toPath)
          if (rc != 0) failed.append(xmlFile.getAbsolutePath.toString)
        }
        if (failed.nonEmpty) {
          println(s"${failed.size} import(s) failed:")
          failed.foreach { println }
          1
        } else 0
      }
    } else if (f.isFile) doImportOne(f.toPath)
    else errorExit(s"${f.getAbsolutePath} is not readable")
  }

  def runImportDataSetSafe(path: Path, projectId: Int = 0): Int = {
    val dsUuid = dsUuidFromPath(path)
    val dsType = dsMetaTypeFromPath(path)
    def importDs = {
      val rc = runImportDataSet(path, dsType)
      if (rc == 0) {
        runGetDataSetInfo(dsUuid)
        if (projectId > 0) addDataSetToProject(dsUuid, projectId) else 0
      } else rc
    }
    println(s"UUID: ${dsUuid.toString}")
    def errorIfNewPath(dsInfo: DataSetMetaDataSet) = println(
      s"ERROR: The dataset UUID (${dsInfo.uuid.toString}) is already "+
      "present in the database but associated with a different path; if you "+
      "want to re-import with the new path, run this command:\n\n  dataset "+
      s"newuuid ${path.toString}\n\nthen run the pbservice command again to "+
      "import the modified dataset.\n")
    def warnIfNewPath(dsInfo: DataSetMetaDataSet) = {
      println(
        s"WARNING: The dataset UUID (${dsInfo.uuid.toString}) is already "+
        "present in the database but associated with a different path; will "+
        "re-import with a different path, but this will overwrite the "+
        "existing record.")
      Thread.sleep(5000)
    }
    getDataSet(dsUuid) match {
      case Success(dsInfo) => {
        if (Paths.get(dsInfo.path) != path) {
          if (isCompatibleVersion()) {
            warnIfNewPath(dsInfo)
            importDs
          } else errorIfNewPath(dsInfo)
        } else println(s"Dataset ${dsUuid.toString} already imported.")
        printDataSetInfo(dsInfo)
      }
      case Failure(err) => {
        println(s"No existing dataset record found")
        importDs
      }
    }
  }

  def runImportDataSet(path: Path, dsType: String, asJson: Boolean = false): Int = {
    logger.info(dsType)
    Try { Await.result(sal.importDataSet(path, dsType), TIMEOUT) } match {
      case Success(jobInfo: EngineJob) => waitForJob(jobInfo.uuid)
      case Failure(err) => errorExit(s"Dataset import failed: $err")
    }
  }

  private def listDataSetFiles(f: File): Array[File] = {

    def metaDataFilter(fn: File): Boolean =
        Try { dsMetaTypeFromPath(fn.toPath) }.isSuccess

    f.listFiles.filter(metaDataFilter) ++ f.listFiles.filter(_.isDirectory).flatMap(listDataSetFiles)
  }


  /**
    * Get Project by (unique) name or Fail future.
    *
    * @param name Project Name
    * @return
    */
  def getProjectByName(name: String): Future[Project] = {
    sal.getProjects.flatMap(projects =>
      failIfNone(s"Unable to find project $name")(projects.find(_.name == name)))
  }

  /**
    * Engine Job Summary
    *
    * @param job    Engine Job
    * @param asJson Convert summary to JSON
    * @return
    */
  def jobSummary(job: EngineJob, asJson: Boolean): String = {
    def jobJsonSummary(job: EngineJob) = job.toJson.prettyPrint.toString

    if (asJson) jobJsonSummary(job)
    else toJobSummary(job)
  }

  def engineDriver(job: EngineJob, maxTime: Option[FiniteDuration]): Future[EngineJob] = {
    // This is blocking polling call is wrapped in a Future
    maxTime.map(t => Future.fromTry(sal.pollForJob(job.id, Some(t))))
        .getOrElse(Future.successful(job))
  }

  /**
    * Run a single DataSet from a file that exists on the server
    *
    * @param path       Path to the remote dataset
    * @param metatype   DataSet metatype
    * @param asJson     summary of the job format
    * @param maxTimeOut If provided, the job will be polled for maxTime until a completed state
    * @return
    */
  def runSingleNonLocalDataSetImport(path: Path, metatype: DataSetMetaTypes.DataSetMetaType, asJson: Boolean, maxTimeOut: FiniteDuration): Future[String] = {
    for {
      job <- sal.importDataSet(path, metatype.toString)
      completedJob <- engineDriver(job, Some(maxTimeOut))
    } yield jobSummary(completedJob, asJson)
  }

  /**
    * Get Existing dataset and companion job, or create a new import dataset job.
    *
    * @param uuid       UUID of dataset
    * @param metatype   DataSet metatype
    * @param path       Path to dataset
    * @param maxTimeOut If provided, the job will be polled for maxTime until a completed state
    * @return
    */
  def getDataSetJobOrImport(uuid: UUID, metatype: DataSetMetaTypes.DataSetMetaType, path: Path, maxTimeOut: Option[FiniteDuration]): Future[EngineJob] = {

    // The dataset has already been imported. Skip the entire job creation process.
    val fx = for {
      ds <- sal.getDataSet(uuid)
      job <- sal.getJob(ds.jobId)
      completedJob <- engineDriver(job, maxTimeOut)
    } yield completedJob

    // Default to creating new Job if the dataset wasn't already imported into the system
    val orCreate = for {
      job <- sal.importDataSet(path, metatype.toString)
      completedJob <- engineDriver(job, maxTimeOut)
    } yield completedJob

    // This should have a tighter exception case
    fx.recoverWith { case NonFatal(_) => orCreate}
  }


  /**
    * Import or Get Already imported dataset from DataSet XML Path
    *
    * @param path Path to PacBio DataSet XML file
    * @param asJson emit the Import DataSet Job as JSON
    * @param maxTimeOut Max time to Poll for blocking job
    * @return
    */
  def runSingleLocalDataSetImport(path: Path, asJson: Boolean, maxTimeOut: FiniteDuration): Future[String] = {
    for {
      m <- Future.fromTry(Try(getDataSetMiniMeta(path)))
      job <- getDataSetJobOrImport(m.uuid, m.metatype, path, Some(maxTimeOut))
      completedJob <- engineDriver(job, Some(maxTimeOut))
      dataset <- sal.getDataSet(m.uuid)
    } yield s"${toDataSetInfoSummary(dataset)}${jobSummary(completedJob, asJson)}"
  }

  /**
    * Recursively import from a Directory of all dataset files ending in *.xml and that are valid
    * PacBio DataSets.
    *
    * @param path       Root Directory to the datasets that will be imported.
    * @param maxTimeOut Max time out PER dataset import
    * @return
    */
  def runRecursiveImportDataSet(path: Path, maxTimeOut: FiniteDuration): Future[String] = {

    // This is not the greatest idea. This should really be a streaming model
    val files:Seq[File] = if (path.toFile.isDirectory) listDataSetFiles(path.toFile).toSeq else Seq.empty[File]

    logger.debug(s"Found ${files.length} PacBio XML files from root dir $path")

    if (files.isEmpty) {
      Future.failed(throw new UnprocessableEntityError(s"No valid XML files found in ${path.toAbsolutePath}"))
    } else {
      // Note, these futures will be run in parallel. This needs a better error communication model.
      val fx = for {
        fxs <- Future.sequence(files.map(f => runSingleLocalDataSetImport(f.toPath, asJson = false, maxTimeOut)))
        summary <- Future.successful(fxs.reduce(_ + "\n" + _))
      } yield summary

      fx
    }
  }


  /**
    * Central Location to import datasets into SL
    *
    * Three cases
    *
    * 1. A single XML file local to where pbservice is executed is provided
    * 2. A directory is provided that is local to where pbservice is executed is provided
    * 3. A non-local import (i.e., referencing a dataset that is local to file system where the server is running
    * but not to where pbservice is exed. This requires the dataset type to be provided). The path must be an XML
    * file, not a directory.
    *
    *
    * Note, the non-local case also can not try to see if the dataset has already been imported. Whereas the
    * local case, the dataset can be read in and checked to see if was already imported (This really should be
    * encapsulated on the server side to return an previously run job?)
    *
    * @param path        Path to the DataSet XML
    * @param datasetType DataSet metadata for non-local imports
    * @param asJson      emit the response as JSON (only when a single XML file is provided)
    * @return A summary of the import process
    */
  def execImportDataSets(path: Path, datasetType: Option[DataSetMetaTypes.DataSetMetaType], asJson: Boolean = false): Future[String] = {
    if (path.toAbsolutePath.toFile.isDirectory) {
      if (asJson) {
        logger.warn("Detected recursive import mode. Disabling JSON output.")
      }
      runRecursiveImportDataSet(path, maxTime)
    } else {
      datasetType match {
        case Some(dsType) => runSingleNonLocalDataSetImport(path, dsType, asJson, maxTime)
        case _ => runSingleLocalDataSetImport(path, asJson, maxTime)
      }
    }
  }

  def runImportDataSets(path: Path,
                        nonLocal: Option[String],
                        projectName: Option[String] = None,
                        asJson: Boolean = false): Int = {

    val projectId = getProjectIdByName(projectName)
    if (projectId < 0) return errorExit("Can't continue with an invalid project.")
    nonLocal match {
      case Some(dsType) =>
        logger.info(s"Non-local file, importing as type $dsType")
        runImportDataSet(path, dsType, asJson)
      case _ =>
        importXmlRecursive(path, listDataSetFiles,
                           (p) => runImportDataSetSafe(p, projectId),
                           (p) => runImportDataSetSafe(p, projectId))
    }
  }

  def runImportRsMovie(path: Path, name: String, projectId: Int = 0): Int = {
    val fileName = path.toAbsolutePath
    if (fileName.endsWith(".fofn") && (name == "")) {
      return errorExit(s"--name argument is required when an FOFN is input")
    }
    val tx = for {
      finalName <- Try { if (name == "") dsNameFromMetadata(path) else name }
      job <- Try { Await.result(sal.convertRsMovie(path, name), TIMEOUT) }
      job <- sal.pollForJob(job.uuid)
      dataStoreFiles <- Try { Await.result(sal.getConvertRsMovieJobDataStore(job.uuid), TIMEOUT) }
    } yield dataStoreFiles

    tx match {
      case Success(dataStoreFiles) =>
        dataStoreFiles.find(_.fileTypeId == FileTypes.DS_HDF_SUBREADS.fileTypeId) match {
          case Some(ds) =>
            runGetDataSetInfo(ds.uuid)
            if (projectId > 0) addDataSetToProject(ds.uuid, projectId) else 0
          case None => errorExit(s"Couldn't find HdfSubreadSet")
        }
      case Failure(err) => errorExit(s"RSII movie import failed: ${err.getMessage}")
    }
  }

  private def listMovieMetadataFiles(f: File): Array[File] = {
    f.listFiles.filter((fn) =>
      matchRsMovieName(fn) && Try { dsNameFromMetadata(fn.toPath) }.isSuccess
    ).toArray ++ f.listFiles.filter(_.isDirectory).flatMap(listMovieMetadataFiles)
  }

  def runImportRsMovies(path: Path,
                        name: String,
                        projectName: Option[String] = None): Int = {
    val projectId = getProjectIdByName(projectName)
    if (projectId < 0) return errorExit("Can't continue with an invalid project.")
    def doImportMany(p: Path): Int = {
      if (name != "") errorExit("--name option not allowed when path is a directory")
      else runImportRsMovie(p, name, projectId)
    }
    importXmlRecursive(path, listMovieMetadataFiles,
                       (p) => runImportRsMovie(p, name, projectId),
                       (p) => doImportMany(p))
  }

  def addDataSetToProject(dsId: IdAble, projectId: Int,
                          verbose: Boolean = false): Int = {
    val tx = for {
      project <- Try { Await.result(sal.getProject(projectId), TIMEOUT) }
      ds <- getDataSet(dsId)
      request <- Try { project.asRequest.appendDataSet(ds.id) }
      projectWithDataSet <- Try { Await.result(sal.updateProject(projectId, request), TIMEOUT) }
      } yield projectWithDataSet

      tx match {
        case Success(p) =>
          if (verbose) printProjectInfo(p) else printMsg(s"Added dataset to project ${p.name}")
        case Failure(err) => errorExit(s"Couldn't add dataset to project: ${err.getMessage}")
    }
  }

  def runDeleteDataSet(datasetId: IdAble): Int = {
    Try { Await.result(sal.deleteDataSet(datasetId), TIMEOUT) } match {
      case Success(response) => printMsg(s"${response.message}")
      case Failure(err) => errorExit(s"Couldn't delete dataset: ${err.getMessage}")
    }
  }

  protected def getProjectIdByName(projectName: Option[String]): Int = {
    if (! projectName.isDefined) return 0
    Try { Await.result(sal.getProjects, TIMEOUT) } match {
      case Success(projects) =>
        projects.map(p => (p.name, p.id)).toMap.get(projectName.get) match {
          case Some(projectId) => projectId
          case None => errorExit(s"Can't find project named '${projectName.get}'", -1)
        }
      case Failure(err) => errorExit(s"Couldn't retrieve projects: ${err.getMessage}", -1)
    }
  }

  def runCreateProject(name: String, description: String): Int = {
    Try { Await.result(sal.createProject(name, description), TIMEOUT) } match {
      case Success(project) => printProjectInfo(project)
      case Failure(err) => errorExit(s"Couldn't create project: ${err.getMessage}")
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

  def runShowPipelines: Int = {
    Await.result(sal.getPipelineTemplates, TIMEOUT).sortWith(_.id > _.id).foreach { pt =>
      println(s"${pt.id}: ${pt.name}")
    }
    0
  }

  def runAnalysisPipeline(jsonPath: Path, block: Boolean): Int = {
    val jsonSrc = Source.fromFile(jsonPath.toFile).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    val analysisOptions = jsonAst.convertTo[PbSmrtPipeServiceOptions]
    runAnalysisPipelineImpl(analysisOptions, block)
  }

  protected def validateEntryPoints(entryPoints: Seq[BoundServiceEntryPoint]): Int = {
    for (entryPoint <- entryPoints) {
      // FIXME
      val datasetId: IdAble = entryPoint.datasetId match {
        case Left(id) => IntIdAble(id)
        case Right(uuid) => UUIDIdAble(uuid)
      }
      getDataSet(datasetId) match {
        case Success(dsInfo) => {
          // TODO check metatype against input
          println(s"Found entry point ${entryPoint.entryId} (datasetId = ${datasetId})")
          printDataSetInfo(dsInfo)
        }
        case Failure(err) => {
          return errorExit(s"can't retrieve datasetId ${datasetId}")
        }
      }
    }
    0
  }

  protected def validatePipelineId(pipelineId: String): Int = {
    Try {
      Await.result(sal.getPipelineTemplate(pipelineId), TIMEOUT)
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
        println(s"Job ${jobInfo.id} UUID ${jobInfo.uuid} started")
        printJobInfo(jobInfo)
        if (block) waitForJob(jobInfo.uuid) else 0
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  protected def importEntryPoint(eid: String, xmlPath: Path): BoundServiceEntryPoint = {
    var dsType = dsMetaTypeFromPath(xmlPath)
    var dsUuid = dsUuidFromPath(xmlPath)
    var xc = runImportDataSetSafe(xmlPath)
    if (xc != 0) throw new Exception(s"Could not import dataset ${eid}:${xmlPath}")
    // this is stupidly inefficient
    val dsId = getDataSet(dsUuid) match {
      case Success(ds) => ds.id
      case Failure(err) => throw new Exception(err.getMessage)
    }
    BoundServiceEntryPoint(eid, dsType, Left(dsId))
  }

  protected def importEntryPointAutomatic(entryPoint: String): BoundServiceEntryPoint = {
    println(s"Importing entry point $entryPoint")
    val epFields = entryPoint.split(':')
    if (epFields.length == 2) {
      importEntryPoint(epFields(0), Paths.get(epFields(1)))
    } else if (epFields.length == 1) {
      val xmlPath = Paths.get(epFields(0))
      val dsType = dsMetaTypeFromPath(xmlPath)
      val eid = entryPointsLookup(dsType)
      importEntryPoint(eid, xmlPath)
    } else throw new Exception(s"Can't interpret argument ${entryPoint}")
  }

  protected def getPipelinePresets(presetXml: Option[Path]): PipelineTemplatePreset = {
    presetXml match {
      case Some(path) => PipelineTemplatePresetLoader.loadFrom(path)
      case _ => defaultPresets
    }
  }

  private def validateEntryPointIds(
      entryPoints: Seq[BoundServiceEntryPoint],
      pipeline: PipelineTemplate): Try[Seq[BoundServiceEntryPoint]] = Try {
    val eidsInput = entryPoints.map(_.entryId).sorted.mkString(", ")
    val eidsTemplate = pipeline.entryPoints.map(_.entryId).sorted.mkString(", ")
    if (eidsInput != eidsTemplate) throw new Exception(
      "Mismatch between supplied and expected entry points: the input "+
      s"datasets correspond to entry points ($eidsInput), while the pipeline "+
      s"${pipeline.id} requires entry points ($eidsTemplate)")
    entryPoints
  }

  // XXX there is a bit of a disconnect between how preset.xml is handled and
  // how options are actually passed to services, so we need to convert them
  // here
  protected def getPipelineServiceOptions(
      jobTitle: String,
      pipelineId: String,
      entryPoints: Seq[BoundServiceEntryPoint],
      presets: PipelineTemplatePreset,
      userTaskOptions: Option[Map[String,String]] = None):
      Try[PbSmrtPipeServiceOptions] = {
    val workflowOptions = Seq[ServiceTaskOptionBase]()
    val userOptions: Seq[ServiceTaskOptionBase] = presets.taskOptions ++
      userTaskOptions.getOrElse(Map[String,String]()).map{
        case (k,v) => k -> ServiceTaskStrOption(k, v)
      }.values
    for {
      _ <- Try {logger.debug("Getting pipeline options from server")}
      pipeline <- Try { Await.result(sal.getPipelineTemplate(pipelineId), TIMEOUT) }
      eps <- validateEntryPointIds(entryPoints, pipeline)
      taskOptions <- Try {PipelineUtils.getPresetTaskOptions(pipeline, userOptions)}
    } yield PbSmrtPipeServiceOptions(jobTitle, pipelineId, entryPoints,
                                     taskOptions, workflowOptions)
  }

  def runPipeline(pipelineId: String,
                  entryPoints: Seq[String],
                  jobTitle: String,
                  presetXml: Option[Path] = None,
                  block: Boolean = true,
                  validate: Boolean = true,
                  taskOptions: Option[Map[String,String]] = None): Int = {
    if (entryPoints.isEmpty) return errorExit("At least one entry point is required")

    val pipelineIdFull = if (pipelineId.split('.').length != 3) s"pbsmrtpipe.pipelines.$pipelineId" else pipelineId

    println(s"pipeline ID: $pipelineIdFull")
    if (validatePipelineId(pipelineIdFull) != 0) return errorExit("Aborting")
    var jobTitleTmp = jobTitle
    if (jobTitle.length == 0) jobTitleTmp = s"pbservice-$pipelineIdFull"
    val tx = for {
      eps <- Try { entryPoints.map(importEntryPointAutomatic) }
      presets <- Try { getPipelinePresets(presetXml) }
      opts <- getPipelineServiceOptions(jobTitleTmp, pipelineIdFull, eps, presets, taskOptions)
      job <- Try { Await.result(sal.runAnalysisPipeline(opts), TIMEOUT) }
    } yield job

    tx match {
      case Success(job) => if (block) waitForJob(job.uuid) else printJobInfo(job)
      case Failure(err) => errorExit(s"Failed to run pipeline: ${err}")//.getMessage}")
    }
  }

  def runTerminateAnalysisJob(jobId: IdAble): Int = {
    def toSummary(m: MessageResponse) = m.message
    println(s"Attempting to terminate Analysis Job ${jobId.toIdString}")
    val fx = for {
      job <- sal.getJob(jobId)
      messageResponse <- sal.terminatePbsmrtpipeJob(job.id)
    } yield messageResponse
    runAndBlock(fx, toSummary, TIMEOUT)
  }

  def runDeleteJob(jobId: IdAble, force: Boolean = true): Int = {
    def deleteJob(job: EngineJob, nChildren: Int): Future[EngineJob] = {
      if (!job.isComplete) {
        if (force) {
          println("WARNING: job did not complete - attempting to terminate")
          if (runTerminateAnalysisJob(jobId) != 0) {
            println("Job termination failed; will delete anyway, but this may have unpredictable side effects")
          }
        } else {
          throw new Exception(s"Can't delete this job because it hasn't completed - try 'pbservice terminate-job ${jobId.toIdString} ...' first, or add the argument --force if you are absolutely certain the job is okay to delete")
        }
      } else if (nChildren > 0) {
        if (force) {
          println("WARNING: job output was used by $nChildren active jobs - deleting it may have unintended side effects")
          Thread.sleep(5000)
        } else {
          throw new Exception(s"Can't delete job ${job.id} because ${nChildren} active jobs used its results as input; add --force if you are absolutely certain the job is okay to delete")
        }
      }
      sal.deleteJob(job.uuid, force = force)
    }
    println(s"Attempting to delete job ${jobId.toIdString}")
    val fx = for {
      job <- Try { Await.result(sal.getJob(jobId), TIMEOUT) }
      children <- Try { Await.result(sal.getJobChildren(job.uuid), TIMEOUT) }
      deleteJob <- Try { Await.result(deleteJob(job, children.size), TIMEOUT) }
      _ <- sal.pollForJob(deleteJob.uuid, Some(maxTime))
    } yield deleteJob

    fx match {
      case Success(j) => println(s"Job ${jobId.toIdString} deleted."); 0
      case Failure(ex) => errorExit(ex.getMessage, 1)
    }
  }

  def manifestSummary(m: PacBioComponentManifest) = s"Component name:${m.name} id:${m.id} version:${m.version}"

  def manifestsSummary(manifests:Seq[PacBioComponentManifest]): String = {
    val headers = Seq("id", "version", "name")
    val table = manifests.map(m => Seq(m.id, m.version, m.name))
    printTable(table, headers)
    ""
  }

  def runGetPacBioManifests(): Int = {
    runAndBlock[Seq[PacBioComponentManifest]](sal.getPacBioComponentManifests, manifestsSummary, TIMEOUT)
  }


  // This is to make it backward compatiblity. Remove this when the other systems are updated
  private def getManifestById(manifestId: String): Future[PacBioComponentManifest] = {
    sal.getPacBioComponentManifests.flatMap { manifests =>
      manifests.find(x => x.id == manifestId) match {
        case Some(m) => Future { m }
        case _ => Future.failed(new ResourceNotFoundError(s"Unable to find $manifestId"))
      }
    }
  }

  def runGetPacBioManifestById(ix: String): Int =
    runAndBlock[PacBioComponentManifest](getManifestById(ix), manifestSummary, TIMEOUT)


  def pacBioDataBundlesSummary(bundles: Seq[PacBioDataBundle]): String = {
    val headers:Seq[String] = Seq("Bundle Id", "Version", "Imported At", "Is Active")
    val table = bundles.map(b => Seq(b.typeId, b.version, b.importedAt.toString(), b.isActive.toString))
    printTable(table, headers)
    // The printTable func should probalby return a string, not an Int
    ""
  }

  def runGetPacBioDataBundles(timeOut: FiniteDuration): Int =
    runAndBlock[Seq[PacBioDataBundle]](sal.getPacBioDataBundles(), pacBioDataBundlesSummary, timeOut)

  def runTsSystemStatus(user: String,
                        comment: String): Int = {
    def toSummary(job: EngineJob) =
      s"Tech support bundle sent.\nUser = ${user}\nComments: $comment"
    println(s"Attempting to send tech support status bundle")
    val fx = for {
      job <- Try { Await.result(sal.runTsSystemStatus(user, comment), TIMEOUT) }
      _ <- sal.pollForJob(job.uuid, Some(maxTime))
    } yield job
    runAndSummary(fx, toSummary)
  }

  def runTsJobBundle(jobId: IdAble,
                     user: String,
                     comment: String): Int = {
    def toSummary(job: EngineJob) =
      s"Tech support job bundle sent.\nJob = ${job.id}; name = ${job.name}\nUser = ${user}\nComments: $comment"
    println(s"Attempting to send tech support failed job bundle")
    val fx = for {
      failedJob <- Try { Await.result(sal.getJob(jobId), TIMEOUT) }
      job <- Try { Await.result(sal.runTsJobBundle(failedJob.id, user, comment), TIMEOUT) }
      _ <- sal.pollForJob(job.uuid, Some(maxTime))
    } yield failedJob
    runAndSummary(fx, toSummary)
  }

}

object PbService extends LazyLogging{


  // Introducing a new pattern to remove duplication. Each Subparser
  // should return a Future[String] where the string is the terse summary of the output
  // and use recoverWith or recover to handle any handle-able exceptions
  // and add local context to the error. The Future Should encapsulate all the necessary
  // steps.

  // These are the ONLY place that should have a blocking call
  // and explicit case match to Success/Failure handing for Try

  def executeBlockAndSummary(fx: Future[String], timeout: FiniteDuration): Int = {
    executeAndSummary(Try(Await.result(fx, timeout)))
  }

  def executeAndSummary(tx: Try[String]): Int = {
    tx match {
      case Success(sx) =>
        println(sx)
        0
      case Failure(ex) =>
        logger.error(s"${ex.getMessage}")
        System.err.println(s"${ex.getMessage}")
        1
    }
  }


  def apply (c: PbServiceParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")

    val sal = new SmrtLinkServiceAccessLayer(c.host, c.port, c.authToken)(actorSystem)
    val ps = new PbService(sal, c.maxTime)

    try {
      c.mode match {
        case Modes.STATUS => executeBlockAndSummary(ps.exeStatus(c.asJson), c.maxTime)
        case Modes.IMPORT_DS => executeBlockAndSummary(ps.execImportDataSets(c.path, c.nonLocal, c.asJson), c.maxTime)
        case Modes.IMPORT_FASTA => ps.runImportFasta(c.path, c.name, c.organism,
                                                     c.ploidy, c.project)
        case Modes.IMPORT_BARCODES => ps.runImportBarcodes(c.path, c.name,
                                                           c.project)
        case Modes.IMPORT_MOVIE => ps.runImportRsMovies(c.path, c.name,
                                                        c.project)
        case Modes.ANALYSIS => ps.runAnalysisPipeline(c.path, c.block)
        case Modes.TEMPLATE => ps.runEmitAnalysisTemplate
        case Modes.PIPELINE => ps.runPipeline(c.pipelineId, c.entryPoints,
                                              c.jobTitle, c.presetXml, c.block,
                                              taskOptions = c.taskOptions)
        case Modes.SHOW_PIPELINES => ps.runShowPipelines
        case Modes.JOB => ps.runGetJobInfo(c.jobId, c.asJson, c.dumpJobSettings, c.showReports)
        case Modes.JOBS => ps.runGetJobs(c.maxItems, c.asJson, c.jobType, c.jobState)
        case Modes.TERMINATE_JOB => ps.runTerminateAnalysisJob(c.jobId)
        case Modes.DELETE_JOB => ps.runDeleteJob(c.jobId, c.force)
        case Modes.DATASET => ps.runGetDataSetInfo(c.datasetId, c.asJson)
        case Modes.DATASETS => ps.runGetDataSets(c.datasetType, c.maxItems, c.asJson, c.searchName, c.searchPath)
        case Modes.DELETE_DATASET => ps.runDeleteDataSet(c.datasetId)
        case Modes.MANIFEST => ps.runGetPacBioManifestById(c.manifestId)
        case Modes.MANIFESTS => ps.runGetPacBioManifests
        case Modes.BUNDLES => ps.runGetPacBioDataBundles(20.seconds)
        case Modes.TS_STATUS => ps.runTsSystemStatus(c.user, c.comment)
        case Modes.TS_JOB => ps.runTsJobBundle(c.jobId, c.user, c.comment)
/*        case Modes.CREATE_PROJECT => ps.runCreateProject(c.name, c.description)*/
        case x => {
          System.err.println(s"Unsupported action '$x'")
          1
        }
      }
    } finally {
      actorSystem.shutdown()
    }
  }
}

object PbServiceApp extends App with LazyLogging{
  def run(args: Seq[String]) = {
    val xc = PbServiceParser.parser.parse(args.toSeq, PbServiceParser.defaults) match {
      case Some(config) =>
        logger.debug(s"Args $config")
        PbService(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
