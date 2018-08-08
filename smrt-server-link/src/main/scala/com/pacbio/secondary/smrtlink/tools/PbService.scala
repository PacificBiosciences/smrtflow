package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.Source
import scala.language.postfixOps
import scala.math._
import scala.util.control.NonFatal
import scala.util.{Failure, Properties, Success, Try}
import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import scopt.OptionParser
import spray.json._
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  ResourceNotFoundError,
  UnprocessableEntityError
}
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.converters._
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._

import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.analysis.pipelines._
import com.pacbio.secondary.smrtlink.analysis.tools._
import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.pacbio.secondary.smrtlink.client._
import com.pacbio.secondary.smrtlink.jobtypes.PbsmrtpipeJobOptions
import com.pacbio.secondary.smrtlink.models.QueryOperators.{
  JobStateQueryOperator,
  StringInQueryOperator,
  StringQueryOperator
}
import com.pacbio.secondary.smrtlink.models._

object Modes {
  sealed trait Mode {
    val name: String
  }
  case object STATUS extends Mode { val name = "status" }
  case object IMPORT_DS extends Mode { val name = "import-dataset" }
  case object IMPORT_FASTA extends Mode { val name = "import-fasta" }
  case object IMPORT_FASTA_GMAP extends Mode { val name = "import-fasta-gmap" }
  case object IMPORT_BARCODES extends Mode { val name = "import-barcodes" }
  case object ANALYSIS extends Mode { val name = "run-analysis" }
  case object TEMPLATE extends Mode { val name = "emit-analysis-template" }
  case object PIPELINE extends Mode { val name = "run-pipeline" }
  case object SHOW_PIPELINES extends Mode { val name = "show-pipelines" }
  case object IMPORT_MOVIE extends Mode { val name = "import-rs-movie" }
  case object JOB extends Mode { val name = "get-job" }
  case object JOBS extends Mode { val name = "get-jobs" }
  case object TERMINATE_JOB extends Mode { val name = "terminate-job" } // This currently ONLY supports Analysis Jobs
  case object DELETE_JOB extends Mode { val name = "delete-job" } // also only analysis jobs
  case object EXPORT_JOB extends Mode { val name = "export-job" }
  case object IMPORT_JOB extends Mode { val name = "import-job" }
  case object UPDATE_JOB extends Mode { val name = "update-job" }
  case object DATASET extends Mode { val name = "get-dataset" }
  case object DATASETS extends Mode { val name = "get-datasets" }
  case object DELETE_DATASET extends Mode { val name = "delete-dataset" }
  case object CREATE_PROJECT extends Mode { val name = "create-project" }
  case object GET_PROJECTS extends Mode { val name = "get-projects" }
  case object MANIFESTS extends Mode { val name = "get-manifests" }
  case object MANIFEST extends Mode { val name = "get-manifest" }
  case object BUNDLES extends Mode { val name = "get-bundles" }
  case object TS_STATUS extends Mode { val name = "ts-status" }
  case object TS_JOB extends Mode { val name = "ts-failed-job" }
  case object ALARMS extends Mode { val name = "get-alarms" }
  case object IMPORT_RUN extends Mode { val name = "import-run" }
  case object GET_RUN extends Mode { val name = "get-run" }
  // case object GET_TOKEN extends Mode { val name = "get-token" } // This is too difficult to wire in for design reasons
}

object PbServiceParser extends CommandLineToolVersion {
  import CommonModelImplicits._
  import CommonModels._

  val VERSION = "0.2.1"
  var TOOL_ID = "pbscala.tools.pbservice"

  // DataSet XML filename ending extension
  final val DS_FILE_EXT = "set.xml"

  private def getSizeMb(fileObj: File): Double = {
    fileObj.length / 1024.0 / 1024.0
  }

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  def showVersion: Unit = showToolVersion(TOOL_ID, VERSION)

  // is there a cleaner way to do this?
  private def entityIdOrUuid(entityId: String): IdAble = {
    // This is not really a good model.
    IdAble.fromString(entityId).getOrElse(IntIdAble(0))
  }

  private def getToken(token: String): String = {
    if (Paths.get(token).toFile.isFile) {
      Source.fromFile(token).getLines.take(1).toList.head
    } else token
  }

  case class CustomConfig(
      mode: Modes.Mode = Modes.STATUS,
      host: String,
      port: Int,
      block: Boolean = false,
      command: CustomConfig => Unit = showDefaults,
      datasetId: IdAble = 0,
      jobId: IdAble = 0,
      runId: UUID = UUID.randomUUID(),
      path: Path = null,
      name: Option[String] = None,
      organism: Option[String] = None,
      ploidy: Option[String] = None,
      maxItems: Int = 25,
      datasetType: DataSetMetaTypes.DataSetMetaType = DataSetMetaTypes.Subread,
      jobType: String = "pbsmrtpipe",
      jobState: Option[String] = None,
      nonLocal: Option[DataSetMetaTypes.DataSetMetaType] = None,
      asJson: Boolean = false,
      dumpJobSettings: Boolean = false,
      pipelineId: String = "",
      jobTitle: String = "",
      entryPoints: Seq[String] = Seq(),
      presetXml: Option[Path] = None,
      taskOptions: Option[Map[String, String]] = None,
      maxTime: FiniteDuration = 30.minutes, // This probably needs to be tuned on a per subparser.
      project: Option[String] = None,
      description: String = "",
      authToken: Option[String] = Properties.envOrNone("PB_SERVICE_AUTH_TOKEN"),
      manifestId: String = "smrtlink",
      showReports: Boolean = false,
      showFiles: Boolean = false,
      searchName: Option[String] = None,
      searchPath: Option[String] = None,
      searchSubJobType: Option[String] = None,
      force: Boolean = false,
      reserved: Boolean = false,
      user: String = Properties
        .envOrNone("PB_SERVICE_AUTH_USER")
        .getOrElse(System.getProperty("user.name")),
      password: Option[String] =
        Properties.envOrNone("PB_SERVICE_AUTH_PASSWORD"),
      usePassword: Boolean = false,
      comment: Option[String] = None,
      includeEntryPoints: Boolean = false,
      blockImportDataSet: Boolean = true, // this is duplicated with "block". This should be collapsed to have consistent behavior within pbservice
      numMaxConcurrentImport: Int = 10, // This number should be tuned.
      tags: Option[String] = None,
      grantRoleToAll: Option[ProjectRequestRole.ProjectRequestRole] = None,
      asXml: Boolean = false
  ) extends LoggerConfig {
    def getName =
      name.getOrElse(
        throw new RuntimeException(
          "--name is required when running this command"))
    def getComment = comment.getOrElse("Sent by pbservice")
  }

  lazy val defaultHost: String =
    Properties.envOrElse("PB_SERVICE_HOST", "localhost")
  lazy val defaultPort: Int =
    Properties.envOrElse("PB_SERVICE_PORT", "8070").toInt
  lazy val defaults = CustomConfig(null, defaultHost, defaultPort)

  lazy val parser = new OptionParser[CustomConfig]("pbservice") {

    private val DS_META_TYPE_NAME =
      "Dataset Meta type name (e.g., subreads, references, PacBio.DataSet.SubreadSet, etc.)"

    private def validateId(entityId: String,
                           entityType: String): Either[String, Unit] = {
      IdAble.fromString(entityId) match {
        case Success(_) => success
        case Failure(_) =>
          failure(
            s"$entityType ID '$entityId' must be a positive integer or a UUID string")
      }
    }

    private def validateJobType(jobType: String): Either[String, Unit] = {
      JobTypeIds
        .fromString(jobType)
        .map(_ => success)
        .getOrElse(failure(
          s"Unrecognized job type '$jobType' Known Jobs types ${JobTypeIds.ALL
            .map(_.id)
            .reduce(_ + "," + _)}"))
    }

    private def validateDataSetMetaType(dsType: String): Either[String, Unit] = {
      val errorMsg =
        s"Invalid DataSet type '$dsType'. Allowed DataSet types ${DataSetMetaTypes.ALL
          .map(_.toString)
          .reduce(_ + "," + _)}"
      DataSetMetaTypes
        .fromAnyName(dsType)
        .map(_ => success)
        .getOrElse(failure(errorMsg))
    }

    head("PacBio SMRTLINK Analysis Services Client", VERSION)

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text s"Hostname of smrtlink server (default: $defaultHost).  Override the default with env PB_SERVICE_HOST."

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text s"Services port on smrtlink server (default: $defaultPort).  Override default with env PB_SERVICE_PORT."

    opt[String]('u', "user") action { (u, c) =>
      c.copy(user = u)
    } text "User ID (requires password if used for authentication); defaults to the value of PB_SERVICE_AUTH_USER if set, otherwise the Unix user account name"

    opt[String]('p', "password") action { (p, c) =>
      c.copy(password = Some(p))
    } text "Authentication password; defaults to the value of env var PB_SERVICE_AUTH_PASSWORD if set"

    opt[Unit]("ask-pass") action { (_, c) =>
      c.copy(usePassword = true)
    } text "Prompt for authentication password"

    opt[String]('t', "token") action { (t, c) =>
      c.copy(authToken = Some(getToken(t)))
    } text "Authentication token (required for project services)"

    // This needs to be folded back into each subparser for clarity. --json isn't supported by every subparser
    opt[Unit]("json") action { (_, c) =>
      c.copy(asJson = true)
    } text "Display output as JSON"

    help("help")

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    // This will handling the adding the logging specific options (e.g., --debug) as well as logging configuration setup
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

    // System Status
    note("\nGET SMRTLINK SYSTEM STATUS\n")
    cmd(Modes.STATUS.name)
      .text("Get SMRT Link System Status")
      .action { (_, c) =>
        c.copy(command = (c) => println("with " + c), mode = Modes.STATUS)
      }

    // Import Datasets
    note("\nIMPORT DATASET\n")
    cmd(Modes.IMPORT_DS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_DS)
    } children (
      arg[File]("dataset-path")
        .required()
        .text(s"""
           |DataSet XML path, a FOFN  (File of File Names) of DataSet XML Files, or a directory containing datasets with files ending with '$DS_FILE_EXT'.
           |When running in directory mode, or FOFN mode with a large number of datasets, it's strongly recommended to run with --non-block
         """.stripMargin)
        .action((p, c) => c.copy(path = p.toPath)),
      opt[Int]("timeout")
        .text(
          s"Maximum time to poll for running job status in seconds (Default ${defaults.maxTime})")
        .action((t, c) => c.copy(maxTime = t.seconds)),
      opt[String]("non-local")
        .validate(validateDataSetMetaType)
        .text("Import non-local dataset require specified the dataset metatype (e.g. PacBio.DataSet.SubreadSet)")
        .action { (t, c) =>
          c.copy(nonLocal = DataSetMetaTypes.fromAnyName(t))
        },
      opt[Unit]("block")
        .text(
          s"Enable blocking mode to poll for job to completion (Default ${defaults.blockImportDataSet}). Mutually exclusive with --non-block")
        .action((t, c) => c.copy(blockImportDataSet = true)),
      opt[Int]('m', "max-concurrent")
        .text(
          s"If in blocking mode, the max number of concurrent dataset imports allowed (Default ${defaults.numMaxConcurrentImport})")
        .action((t, c) => c.copy(numMaxConcurrentImport = t)),
      opt[Unit]("non-block")
        .text(
          s"Disable blocking mode to poll for job to completion. Import Job will only be submitted. (Default ${!defaults.blockImportDataSet}). Mutually exclusive with --block")
        .action((t, c) => c.copy(blockImportDataSet = false)),
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with this dataset"
    ) text "Import PacBio DataSet(s) into SMRTLink"

    private def argsImportFasta(dsType: String) = Seq(
      arg[File]("fasta-path") required () action { (p, c) =>
        c.copy(path = p.toPath)
      } text "FASTA path",
      opt[String]("name") action { (name, c) =>
        c.copy(name = Some(name)) // do we need to check that this is non-blank?
      } text s"Name of $dsType",
      opt[String]("organism") action { (organism, c) =>
        c.copy(organism = Some(organism))
      } text "Organism",
      opt[String]("ploidy") action { (ploidy, c) =>
        c.copy(ploidy = Some(ploidy))
      } text "Ploidy",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t.seconds)
      } text s"Maximum time to poll for running job status in seconds (Default ${defaults.maxTime})",
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with this reference"
    )

    note("\nIMPORT FASTA\n")
    cmd(Modes.IMPORT_FASTA.name)
      .action { (_, c) =>
        c.copy(command = (c) => println(c), mode = Modes.IMPORT_FASTA)
      }
      .children(argsImportFasta("ReferenceSet"): _*)
      .text("Import Reference FASTA")

    note("\nIMPORT FASTA GMAP\n")
    cmd(Modes.IMPORT_FASTA_GMAP.name)
      .action { (_, c) =>
        c.copy(command = (c) => println(c), mode = Modes.IMPORT_FASTA_GMAP)
      }
      .children(argsImportFasta("GmapReferenceSet"): _*)
      .text("Import GMAP Reference FASTA")

    note("\nIMPORT BARCODE\n")
    cmd(Modes.IMPORT_BARCODES.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_BARCODES)
    } children (
      arg[File]("fasta-path") required () action { (p, c) =>
        c.copy(path = p.toPath)
      } text "FASTA path",
      arg[String]("name") required () action { (name, c) =>
        c.copy(name = Some(name))
      } text "Name of BarcodeSet",
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with these barcodes"
    ) text "Import Barcodes FASTA"

    note("\nIMPORT RSII MOVIE\n")
    cmd(Modes.IMPORT_MOVIE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_MOVIE)
    } children (
      arg[File]("metadata-xml-path") required () action { (p, c) =>
        c.copy(path = p.toPath)
      } text "Path to RS II movie metadata XML file (or directory)",
      opt[String]("name") action { (name, c) =>
        c.copy(name = Some(name))
      } text "Name of imported HdfSubreadSet",
      opt[String]("project") action { (p, c) =>
        c.copy(project = Some(p))
      } text "Name of project associated with this dataset"
    ) text "Import RS II movie metadata XML legacy format as HdfSubreadSet"

    note("\nRUN ANALYSIS JOB\n")
    cmd(Modes.ANALYSIS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.ANALYSIS)
    } children (
      arg[File]("json-file") required () action { (p, c) =>
        c.copy(path = p.toPath)
      } text "JSON config file", // TODO validate json format
      opt[Unit]("block") action { (_, c) =>
        c.copy(block = true)
      } text "Block until job completes",
      opt[Int]("timeout") action { (t, c) =>
        c.copy(maxTime = t.seconds)
      } text s"Maximum time (in seconds) to poll for running job status (Default ${defaults.maxTime})"
    ) text "Run a pbsmrtpipe analysis pipeline from a JSON config file"

    note("\nTERMINATE ANALYSIS JOB\n")
    cmd(Modes.TERMINATE_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TERMINATE_JOB)
    } children (
      arg[Int]("job-id") required () action { (p, c) =>
        c.copy(jobId = p)
      } text "SMRT Link Analysis Job Id"
    ) text "Terminate a SMRT Link Analysis Job By Int Id in the RUNNING state"

    note("\nDELETE JOB\n")
    cmd(Modes.DELETE_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DELETE_JOB)
    } children (
      arg[String]("job-id") required () action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } validate { i =>
        validateId(i, "Job")
      } text "Job ID",
      opt[Unit]("force") action { (_, c) =>
        c.copy(force = true)
      } text "Force job delete even if it is still running or has active child jobs (NOT RECOMMENDED)"
    ) text "Delete a pbsmrtpipe job, including all output files"

    note("\nEXPORT JOB\n")
    cmd(Modes.EXPORT_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.EXPORT_JOB)
    } children (
      arg[String]("job-id") required () action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } validate { i =>
        validateId(i, "Job")
      } text "Job ID",
      opt[String]("output-dir") action { (p, c) =>
        c.copy(path = Paths.get(p))
      } text "Output directory for job ZIP file; must be writable by smrtlink",
      opt[Unit]("include-entry-points") action { (_, c) =>
        c.copy(includeEntryPoints = true)
      } text "Include input datasets in exported job ZIP file"
    ) text "Export a job to a ZIP file"

    note("\nIMPORT JOB\n")
    cmd(Modes.IMPORT_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_JOB)
    } children (
      arg[File]("zip-path") required () action { (f, c) =>
        c.copy(path = f.toPath)
      } text "Path to ZIP file exported by a SMRT Link server"
    ) text "Import a SMRT Link job from a ZIP file"

    note("\nUPDATE JOB\n")
    cmd(Modes.UPDATE_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.UPDATE_JOB)
    } children (
      arg[String]("job-id") required () action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } validate { i =>
        validateId(i, "Job")
      } text "Job ID",
      opt[String]("name") action { (n, c) =>
        c.copy(name = Some(n))
      } text "New job name",
      opt[String]("comment") action { (s, c) =>
        c.copy(comment = Some(s))
      } text "New job comments field (optional)",
      opt[String]("tags") action { (s, c) =>
        c.copy(tags = Some(s))
      } text "Updated job tags field (optional)"
    ) text "Update SMRT Link job name or comment"

    note("\nEMIT ANALYSIS JSON TEMPLATE\n")
    cmd(Modes.TEMPLATE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TEMPLATE)
    } children () text "Emit an analysis.json template to stdout that can be run using 'run-analysis'"

    note("\nSHOW ANALYSIS PIPELINE TEMPLATES\n")
    cmd(Modes.SHOW_PIPELINES.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.SHOW_PIPELINES)
    } text "Display a list of available pbsmrtpipe pipelines"

    note("\nRUN ANALYSIS PIPELINE\n")
    cmd(Modes.PIPELINE.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.PIPELINE)
    } children (
      arg[String]("pipeline-id") required () action { (p, c) =>
        c.copy(pipelineId = p)
      } text "Pipeline ID to run",
      opt[String]('e', "entry-point") minOccurs (1) maxOccurs (1024) action {
        (e, c) =>
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
      opt[Map[String, String]]("task-options")
        .valueName("k1=v1,k2=v2...")
        .action { (x, c) =>
          c.copy(taskOptions = Some(x))
        }
        .text("Pipeline task options as comma-separated option_id=value list")
    ) text "Run a pbsmrtpipe pipeline by name on the server"

    note("\nGET SMRTLINK JOB\n")
    cmd(Modes.JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.JOB)
    } children (
      arg[String]("job-id") required () action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } validate { i =>
        validateId(i, "Job")
      } text "Job ID",
      opt[Unit]("show-settings") action { (_, c) =>
        c.copy(dumpJobSettings = true)
      } text "Print JSON settings for job, suitable for input to 'pbservice run-analysis'",
      opt[Unit]("show-reports") action { (_, c) =>
        c.copy(showReports = true)
      } text "Display job report attributes",
      opt[Unit]("show-files") action { (_, c) =>
        c.copy(showFiles = true)
      } text "Display job output files from pbsmrtpipe datastore"
    ) text "Show job details"

    note("\nGET SMRTLINK JOB LIST\n")
    cmd(Modes.JOBS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.JOBS)
    } children (
      opt[Int]('m', "max-items") action { (m, c) =>
        c.copy(maxItems = m)
      } text s"Max number of jobs to show (Default: ${defaults.maxItems})",
      opt[String]('t', "job-type") action { (t, c) =>
        c.copy(jobType = t)
      } validate { t =>
        validateJobType(t)
      } text s"Only retrieve jobs of specified type (Default: ${defaults.jobType})",
      opt[String]('s', "job-state") action { (s, c) =>
        c.copy(jobState = Some(s)) // This should validate the job state.
      } text "Only display jobs in specified state (e.g., CREATED, SUCCESSFUL, RUNNING, FAILED)",
      opt[String]("search-name") action { (n, c) =>
        c.copy(searchName = Some(n))
      } text "Search for jobs whose 'name' field matches the specified string. Supported syntax 'Alpha' or 'in:Alpha,Beta'",
      opt[String]("search-pipeline") action { (p, c) =>
        c.copy(searchSubJobType = Some(p))
      } text "Search for jobs by subJobTypeId field, i.e. pipeline ID (applies to pbsmrtpipe jobs only)"
    )

    note("\nGET SMRTLINK DATASET DETAILS\n")
    cmd(Modes.DATASET.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DATASET)
    } children (
      arg[String]("dataset-id") required () action { (i, c) =>
        c.copy(datasetId = entityIdOrUuid(i))
      } validate { i =>
        validateId(i, "Dataset")
      } text "Dataset ID"
    ) text "Show dataset details"

    note("\nGET SMRTLINK DATASET LIST\n")
    cmd(Modes.DATASETS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DATASETS)
    } children (
      opt[String]('t', "dataset-type")
        .text(DS_META_TYPE_NAME)
        .validate(t => validateDataSetMetaType(t))
        .action { (t, c) =>
          c.copy(datasetType = DataSetMetaTypes.fromAnyName(t).get)
        },
      opt[Int]('m', "max-items") action { (m, c) =>
        c.copy(maxItems = m)
      } text "Max number of Datasets to show",
      opt[String]("search-name") action { (n, c) =>
        c.copy(searchName = Some(n))
      } text "Search for datasets whose 'name' field matches the specified string. Supported syntax 'Alpha' or 'in:Alpha,Beta'",
      opt[String]("search-path") action { (p, c) =>
        c.copy(searchPath = Some(p))
      } text "Search for datasets whose 'path' field matches the specified string. Supported syntax '/path/alpha.xml' or 'in:/alpha.xml,/path/beta.xml'"
    )

    note("\nDELETE SMRTLINK DATASET\n")
    cmd(Modes.DELETE_DATASET.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DELETE_DATASET)
    } children (
      arg[String]("dataset-id") required () action { (i, c) =>
        c.copy(datasetId = entityIdOrUuid(i))
      } validate { i =>
        validateId(i, "Dataset")
      } text "Dataset ID"
    ) text "Soft-delete of a dataset (won't remove files)"

    note("\nGET SMRTLINK MANIFESTS\n")
    cmd(Modes.MANIFESTS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.MANIFESTS)
    } text "Get a List of SMRT Link PacBio Component Versions"

    note("\nGET SMRTLINK MANIFEST DETAILS\n")
    cmd(Modes.MANIFEST.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.MANIFEST)
    } children (
      opt[String]('i', "manifest-id") action { (t, c) =>
        c.copy(manifestId = t)
      } text s"Manifest By Id (Default: ${defaults.manifestId})"
    ) text "Get PacBio Component Manifest version by Id."

    note("\nGET PACBIO LOADED BUNDLES\n")
    cmd(Modes.BUNDLES.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.BUNDLES)
    } text "Get a List of PacBio Data Bundles registered to SMRT Link"

    cmd(Modes.CREATE_PROJECT.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.CREATE_PROJECT)
    } children (
      arg[String]("name") required () action { (n, c) =>
        c.copy(name = Some(n))
      } text "Project name",
      arg[String]("description") required () action { (d, c) =>
        c.copy(description = d)
      } text "Project description",
      opt[String]("grant-role-to-all") action { (s, c) =>
        c.copy(grantRoleToAll = Some(ProjectRequestRole.fromString(s)))
      } text "Role to grant to all other users in the new project.  Choices are CAN_EDIT, CAN_VIEW, and NONE; the default is NONE, i.e. only the project owner will be able to view or edit the project and associated datasets."
    ) text "Start a new project (requires authentication)"

    cmd(Modes.GET_PROJECTS.name)
      .action { (_, c) =>
        c.copy(command = (c) => println(c), mode = Modes.GET_PROJECTS)
      }
      .text("Show a list of available projects (requires authentication)")

    note("\nTECH SUPPORT SYSTEM STATUS REQUEST\n")
    cmd(Modes.TS_STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TS_STATUS)
    } children (
      opt[String]("comment") action { (s, c) =>
        c.copy(comment = Some(s))
      } text s"Comments to include (default: ${defaults.comment})"
    ) text "Send system status report to PacBio Tech Support"

    note("\nTECH SUPPORT FAILED JOB REQUEST\n")
    cmd(Modes.TS_JOB.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.TS_JOB)
    } children (
      arg[String]("job-id") required () action { (i, c) =>
        c.copy(jobId = entityIdOrUuid(i))
      } text "ID of job whose details should be sent to tech support",
      opt[String]("comment") action { (s, c) =>
        c.copy(comment = Some(s))
      } text s"Comments to include (default: ${defaults.comment})"
    ) text "Send failed job information to PacBio Tech Support"

    note("\nSMRT Link ALARMS\n")
    cmd(Modes.ALARMS.name)
      .action { (_, c) =>
        c.copy(command = (c) => println(c), mode = Modes.ALARMS)
      }
      .text("Get a List of SMRT Link System Alarm")

    note("\nIMPORT RUN DESIGN\n")
    cmd(Modes.IMPORT_RUN.name)
      .action { (_, c) =>
        c.copy(command = (c) => println(c), mode = Modes.IMPORT_RUN)
      }
      .text("Import a SMRT Link run design XML")
      .children(
        arg[File]("xml-file")
          .required()
          .action((f, c) => c.copy(path = f.toPath))
          .text("Run design XML file"),
        opt[Unit]("reserved")
          .action((_, c) => c.copy(reserved = true))
          .text("Set reserved=True")
      )

    note("\tRETRIEVE RUN DESIGN\n")
    cmd(Modes.GET_RUN.name)
      .action { (_, c) =>
        c.copy(command = (c) => println(c), mode = Modes.GET_RUN)
      }
      .text("Display a SMRT Link instrument run design")
      .children(
        arg[String]("run-id")
          .required()
          .action((i, c) => c.copy(runId = UUID.fromString(i)))
          .text("Run unique ID (UUID)"),
        opt[Unit]("xml")
          .action((_, c) => c.copy(asXml = true))
          .text("Output run design XML")
      )

    // Don't show the help if validation error
    override def showUsageOnError = false
  }
}

class PbService(val sal: SmrtLinkServiceClient, val maxTime: FiniteDuration)
    extends LazyLogging
    with ClientUtils
    with DaoFutureUtils {
  import CommonModelImplicits._
  import CommonModels._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  // the is the default for timeout for common tasks
  protected val TIMEOUT = 30 seconds
  private lazy val defaultPresets = PipelineTemplatePreset(
    "default",
    "any",
    Seq[ServiceTaskOptionBase](),
    Seq[ServiceTaskOptionBase]())
  private lazy val rsMovieName =
    """m([0-9]{6})_([0-9a-z]{5,})_([0-9a-z]{5,})_c([0-9]{16,})_(\w\d)_(\w\d)""".r

  private def matchRsMovieName(file: File): Boolean =
    rsMovieName.findPrefixMatchOf(file.getName).isDefined

  protected def statusSummary(status: ServiceStatus): String = {
    val headers = Seq("ID", "UUID", "Version", "Message")
    val table = Seq(
      Seq(status.id.toString,
          status.uuid.toString,
          status.version,
          status.message))
    toTable(table, headers)
  }

  // This really needs a count call on each dataset type
  // or a direct summary endpoint. This doesn't really
  // work with the new query endpoint.
  protected def systemDataSetSummary(): Future[String] = {
    for {
      numSubreadSets <- sal.getSubreadSets().map(_.length)
      numHdfSubreadSets <- sal.getHdfSubreadSets().map(_.length)
      numReferenceSets <- sal.getReferenceSets().map(_.length)
      numGmapReferenceSets <- sal.getGmapReferenceSets().map(_.length)
      numAlignmenSets <- sal.getAlignmentSets().map(_.length)
      numBarcodeSets <- sal.getBarcodeSets().map(_.length)
      numConsensusReadSets <- sal.getConsensusReadSets().map(_.length)
      numConsensusAlignmentSets <- sal
        .getConsensusAlignmentSets()
        .map(_.length)
      numContigSets <- sal.getContigSets().map(_.length)
      numTranscriptSets <- sal.getTranscriptSets().map(_.length)
    } yield s"""
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
        |TranscriptSets         : $numTranscriptSets
      """.stripMargin
  }

  protected def systemJobSummary(): Future[String] = {
    for {
      numImportJobs <- sal.getImportJobs().map(_.length)
      numMergeJobs <- sal.getMergeJobs().map(_.length)
      numAnalysisJobs <- sal.getAnalysisJobs().map(_.length)
      numConvertFastaJobs <- sal.getFastaConvertJobs().map(_.length)
      numConvertFastaBarcodeJobs <- sal.getBarcodeConvertJobs().map(_.length)
      numTsSystemBundleJobs <- sal.getTsSystemBundleJobs().map(_.length)
      numTsFailedJobBundleJob <- sal.getTsFailedJobBundleJobs().map(_.length)
      numDbBackUpJobs <- sal.getDbBackUpJobs().map(_.length)
    } yield s"""
        |System Job Summary by job type:
        |
        |Import DataSet                    : $numImportJobs
        |Merge DataSet                     : $numMergeJobs
        |Analysis                          : $numAnalysisJobs
        |Convert Fasta to ReferenceSet     : $numConvertFastaJobs
        |Convert Fasta to BarcodeSet       : $numConvertFastaBarcodeJobs
      """.stripMargin
  }

  /**
    * Util to get the Pbservice Client and Server compatibility status
    *
    * @param status Remote SMRT Link Server status
    * @return
    */
  protected def compatibilitySummary(status: ServiceStatus): Future[String] = {
    def msg(sx: String) =
      s"Pbservice ${Constants.SMRTFLOW_VERSION} $sx compatible with Server ${status.version}"

    def fx = isVersionGteSystemVersion(status).map(_ => msg("IS"))

    fx.recover { case NonFatal(_) => msg("IS NOT") }
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
        statusSummaryMsg <- Future
          .successful(status)
          .map(status => statusSummary(status))
        compatSummaryMsg <- compatibilitySummary(status)
        dataSetSummaryMsg <- systemDataSetSummary()
        jobSummaryMsg <- systemJobSummary()
      } yield
        s"$statusSummaryMsg\n$compatSummaryMsg\n$dataSetSummaryMsg\n$jobSummaryMsg"
    }

    def statusJsonSummary(status: ServiceStatus): Future[String] =
      Future.successful(status.toJson.prettyPrint.toString)

    val summary: (ServiceStatus => Future[String]) =
      if (asJson) statusJsonSummary else statusFullSummary
    sal.getStatus.flatMap(summary)
  }

  def runGetDataSetInfo(datasetId: IdAble,
                        asJson: Boolean = false): Future[String] = {
    for {
      ds <- sal.getDataSet(datasetId)
      summary <- Future.successful {
        if (asJson) ds.toJson.prettyPrint
        else toDataSetInfoSummary(ds)
      }
    } yield summary
  }

  def runGetDataSets(dsType: DataSetMetaTypes.DataSetMetaType,
                     maxItems: Int,
                     asJson: Boolean = false,
                     searchName: Option[String] = None,
                     searchPath: Option[String] = None): Future[String] = {

    DataSetSearchCriteria.default.copy(limit = maxItems)

    val qName = searchName.flatMap(StringQueryOperator.fromString)
    val qPath = searchPath.flatMap(StringQueryOperator.fromString)

    val searchCriteria =
      DataSetSearchCriteria.default.copy(limit = maxItems,
                                         name = qName,
                                         path = qPath)

    def fx: Future[Seq[(ServiceDataSetMetadata, JsValue)]] = dsType match {
      case DataSetMetaTypes.Subread =>
        sal
          .getSubreadSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.HdfSubread =>
        sal
          .getHdfSubreadSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.Barcode =>
        sal
          .getBarcodeSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.Reference =>
        sal
          .getReferenceSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.GmapReference =>
        sal
          .getGmapReferenceSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.Contig =>
        sal
          .getContigSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.Alignment =>
        sal
          .getAlignmentSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.AlignmentCCS =>
        sal
          .getConsensusAlignmentSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.CCS =>
        sal
          .getConsensusReadSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
      case DataSetMetaTypes.Transcript =>
        sal
          .getTranscriptSets(Some(searchCriteria))
          .map(_.map(ds => (ds, ds.toJson)))
    }

    def jsonPrinter(records: Seq[JsValue]): String =
      records.map(_.prettyPrint).mkString(",\n")

    def tablePrinter(records: Seq[ServiceDataSetMetadata]): String = {
      val table = records.map { ds =>
        Seq(ds.id.toString, ds.uuid.toString, ds.name, ds.path)
      }
      toTable(table, Seq("ID", "UUID", "Name", "Path"))
    }

    def printer(records: Seq[(ServiceDataSetMetadata, JsValue)]): String = {
      if (asJson) jsonPrinter(records.map(_._2))
      else tablePrinter(records.map(_._1))
    }

    fx.map(printer)
  }

  def runGetJobInfo(jobId: IdAble,
                    asJson: Boolean = false,
                    dumpJobSettings: Boolean = false,
                    showReports: Boolean = false,
                    showFiles: Boolean = false): Future[String] = {
    for {
      job <- sal.getJob(jobId)
      jobInfo <- Future.successful(formatJobInfo(job, asJson, dumpJobSettings))
      reportFiles <- {
        if (showReports) sal.getJobReports(job.uuid)
        else Future.successful(Seq.empty[DataStoreReportFile])
      }
      reports <- Future.sequence {
        reportFiles.map { dsr =>
          sal.getJobReport(job.uuid, dsr.dataStoreFile.uuid)
        }
      }
      reportInfo <- Future.successful {
        reports.map(r => formatReportAttributes(r)).mkString("\n")
      }
      datastore <- {
        if (showFiles) sal.getJobDataStore(jobId).map(ds => Some(ds))
        else Future.successful(None)
      }
      fileInfo <- Future.successful {
        datastore
          .map(ds => formatDataStoreFiles(ds, Some(Paths.get(job.path))))
          .getOrElse("")
      }
    } yield jobInfo + reportInfo + fileInfo
  }

  def runGetJobs(maxItems: Int,
                 asJson: Boolean = false,
                 jobType: String = "pbsmrtpipe",
                 jobState: Option[String] = None,
                 searchName: Option[String] = None,
                 searchSubJobType: Option[String] = None): Future[String] = {
    val qJobState = jobState.flatMap(JobStateQueryOperator.fromString)
    val qName = searchName.flatMap(StringQueryOperator.fromString)
    val qSubJobType = searchSubJobType.flatMap(StringQueryOperator.fromString)

    val searchCriteria =
      JobSearchCriteria.default.copy(limit = maxItems,
                                     name = qName,
                                     state = qJobState,
                                     subJobTypeId = qSubJobType)
    sal
      .getJobsByType(jobType, Some(searchCriteria))
      .map(jobs => toJobsSummary(jobs, asJson))
  }

  private def getDataSetResult(
      jobId: IdAble,
      dsType: FileTypes.DataSetBaseType): Future[DataSetMetaDataSet] = {
    val errMsg = s"Can't find datastore file of type ${dsType.fileTypeId}"
    for {
      datastore <- sal.getJobDataStore(jobId)
      dsId <- {
        datastore
          .find(_.fileTypeId == dsType.fileTypeId)
          .map(f => Future.successful(f.uuid))
          .getOrElse(Future.failed(new RuntimeException(errMsg)))
      }
      dataset <- sal.getDataSet(dsId)
    } yield dataset
  }

  private def importFasta(path: Path,
                          dsType: FileTypes.DataSetBaseType,
                          runJob: (Option[Int]) => Future[EngineJob],
                          projectName: Option[String]): Future[String] = {
    for {
      projectId <- getProjectIdByName(projectName)
      contigs <- Future.successful(PacBioFastaValidator.validate(path))
      job <- runJob(projectId)
      successfulJob <- engineDriver(job, Some(maxTime))
      dataset <- getDataSetResult(job.id, dsType)
    } yield toDataSetInfoSummary(dataset)
  }

  def runImportFasta(path: Path,
                     name: String,
                     organism: Option[String],
                     ploidy: Option[String],
                     projectName: Option[String] = None): Future[String] = {
    importFasta(
      path,
      FileTypes.DS_REFERENCE,
      (projectId) =>
        sal.importFasta(path,
                        name,
                        organism.getOrElse("unknown"),
                        ploidy.getOrElse("unknown"),
                        projectId = projectId),
      projectName
    )
  }

  def runImportBarcodes(path: Path,
                        name: String,
                        projectName: Option[String] = None): Future[String] =
    importFasta(path,
                FileTypes.DS_BARCODE,
                (projectId) => sal.importFastaBarcodes(path, name, projectId),
                projectName)

  def runImportFastaGmap(
      path: Path,
      name: String,
      organism: Option[String],
      ploidy: Option[String],
      projectName: Option[String] = None): Future[String] = {
    importFasta(
      path,
      FileTypes.DS_GMAP_REF,
      (projectId) =>
        sal.importFastaGmap(path,
                            name,
                            organism.getOrElse("unknown"),
                            ploidy.getOrElse("unknown"),
                            projectId = projectId),
      projectName
    )
  }

  /**
    * List all well-formed PacBio DataSet XML Files ending with *set.xml
    *
    * @param f Root Directory
    * @return
    */
  private def listDataSetFiles(f: File): Array[File] = {

    val ext = "set.xml"

    def getPacBioXMLFiles(f: File, files: Map[UUID, Path]): Map[UUID, Path] = {

      if (f.isDirectory) {
        f.listFiles()
          .map(x => getPacBioXMLFiles(x, files))
          .reduceLeftOption(_ ++ _)
          .getOrElse(files)
      } else if (f.getName.endsWith(ext)) {
        Try(getDataSetMiniMeta(f.toPath))
          .map(m => Map(m.uuid -> f.toPath) ++ files)
          .getOrElse(files)
      } else {
        files
      }
    }

    getPacBioXMLFiles(f, Map.empty[UUID, Path]).values.map(_.toFile).toArray
  }

  /**
    * Engine Job Summary
    *
    * @param job    Engine Job
    * @param asJson Convert summary to JSON
    * @return
    */
  protected def jobSummary(job: EngineJob, asJson: Boolean): String = {
    def jobJsonSummary(job: EngineJob) = job.toJson.prettyPrint.toString

    if (asJson) jobJsonSummary(job)
    else toJobSummary(job)
  }

  private def jobFailure(job: EngineJob) = {
    logger.error(s"Job ${job.id} failed!")
    Future.failed(
      new RuntimeException(s"Error running job: ${job.errorMessage}"))
  }

  protected def jobSummaryOrFailure(job: EngineJob,
                                    asJson: Boolean): Future[String] = {
    if (!job.isSuccessful) {
      jobFailure(job)
    } else {
      Future.successful(jobSummary(job, asJson))
    }
  }

  protected def engineDriver(
      job: EngineJob,
      maxTime: Option[FiniteDuration]): Future[EngineJob] = {
    // This is blocking polling call is wrapped in a Future
    maxTime
      .map(t => Future.fromTry(sal.pollForCompletedJob(job.id, Some(t))))
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
  protected def runSingleNonLocalDataSetImport(
      path: Path,
      metatype: DataSetMetaTypes.DataSetMetaType,
      asJson: Boolean,
      maxTimeOut: Option[FiniteDuration],
      projectName: Option[String]): Future[String] = {
    for {
      projectId <- getProjectIdByName(projectName)
      job <- sal.importDataSet(path, metatype, projectId)
      completedJob <- engineDriver(job, maxTimeOut)
      summary <- jobSummaryOrFailure(completedJob, asJson)
    } yield summary
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
  protected def getDataSetJobOrImport(
      uuid: UUID,
      metatype: DataSetMetaTypes.DataSetMetaType,
      path: Path,
      maxTimeOut: Option[FiniteDuration],
      projectName: Option[String]): Future[EngineJob] = {

    def logIfPathIsDifferent(
        ds: DataSetMetaDataSet): Future[DataSetMetaDataSet] = {
      if (ds.path != path.toString) {
        val msg =
          s"DataSet Path on Server will attempted to be updated to $path from ${ds.path}"
        logger.warn(msg)
        System.err.println(msg)
        // this triggers the recoverWith block that re-imports
        Future.failed(new RuntimeException(msg))
      } else {
        Future.successful(ds)
      }
    }

    // The dataset has already been imported. Skip the entire job creation process.
    // This assumes that the Job was successful (because the datastore was imported)
    def fx =
      for {
        ds <- sal.getDataSet(uuid)
        _ <- logIfPathIsDifferent(ds)
        job <- sal.getJob(ds.jobId)
        completedJob <- engineDriver(job, maxTimeOut)
      } yield completedJob

    // Default to creating new Job if the dataset wasn't already imported into the system
    def orCreate =
      for {
        projectId <- getProjectIdByName(projectName)
        job <- sal.importDataSet(path, metatype, projectId)
        completedJob <- engineDriver(job, maxTimeOut)
      } yield completedJob

    // This should have a tighter exception case
    fx.recoverWith { case NonFatal(_) => orCreate }
  }

  /**
    * Import or Get Already imported dataset from DataSet XML Path
    *
    * @param path Path to PacBio DataSet XML file
    * @param asJson emit the Import DataSet Job as JSON
    * @param maxTimeOut Max time to Poll for blocking job
    * @return
    */
  protected def runSingleLocalDataSetImport(
      path: Path,
      asJson: Boolean,
      maxTimeOut: Option[FiniteDuration],
      projectName: Option[String]): Future[EngineJob] = {
    logger.debug(s"Attempting to import dataset from $path")

    for {
      m <- Future.fromTry(Try(getDataSetMiniMeta(path)))
      job <- getDataSetJobOrImport(m.uuid,
                                   m.metatype,
                                   path,
                                   maxTimeOut,
                                   projectName)
      completedJob <- engineDriver(job, maxTimeOut)
    } yield completedJob
  }

  /**
    * The Import Summary is dependent on the input provided and if the job is blocking.
    *
    * @param job     Engine Job
    * @param dataset DataSet metadata (only if the job is blocking)
    * @param asJson  emit results as JSON. When asJson is provided, ONLY the Job entity is returned.
    * @return
    */
  protected def importSummary(job: EngineJob,
                              dataset: Option[DataSetMetaDataSet],
                              asJson: Boolean): String = {
    if (asJson) {
      jobSummary(job, asJson)
    } else {
      dataset
        .map(d => s"${toDataSetInfoSummary(d)}${jobSummary(job, asJson)}")
        .getOrElse(jobSummary(job, asJson))
    }
  }

  protected def runSingleLocalDataSetImportWithSummary(
      path: Path,
      asJson: Boolean,
      maxTimeOut: Option[FiniteDuration],
      projectName: Option[String]): Future[String] = {

    // Only when the maxTimeOut is provided will the job poll and complete. Then the dataset summary can
    // be displayed
    def generateSummary(job: EngineJob, dsUUID: UUID): Future[String] = {
      if (job.isSuccessful) {
        maxTimeOut
          .map(
            t =>
              sal
                .getDataSet(dsUUID)
                .map(m => importSummary(job, Some(m), asJson)))
          .getOrElse(Future.successful(importSummary(job, None, asJson)))
      } else {
        jobFailure(job)
      }
    }

    for {
      m <- Future.fromTry(Try(getDataSetMiniMeta(path)))
      completedJob <- runSingleLocalDataSetImport(path,
                                                  asJson,
                                                  maxTimeOut,
                                                  projectName)
      summary <- generateSummary(completedJob, m.uuid)
    } yield summary
  }

  protected def validateJobWasSuccessful(job: EngineJob) = job.isSuccessful
  protected def validateJobNotFailed(job: EngineJob) = !job.hasFailed

  /**
    * Recursively import from a Directory of all dataset files ending in *.xml and that are valid
    * PacBio DataSets.
    *
    * This is not the greatest idea. This should really be a streaming model
    *
    * @param files      List of files to import
    * @return
    */
  private def runMultiImportXml(
      files: Seq[File],
      runImport: Path => Future[EngineJob],
      maxTimeOut: Option[FiniteDuration],
      numMaxConcurrentImport: Int): Future[String] = {

    // If maxTimeOut is provided, we poll the job to completion and expect the Job to be in a successful state
    // If not provided, we only expect the job is not in a failure state (e.g, CREATED, RUNNING)
    val jobFilter: (EngineJob => Boolean) = maxTimeOut match {
      case Some(_) => validateJobWasSuccessful
      case _ => validateJobNotFailed
    }

    logger.info(s"Attempting to import ${files.length} PacBio DataSet(s)")
    if (files.isEmpty) {
      // Not sure if this should raise
      Future.failed(
        UnprocessableEntityError(s"No valid XML files found to process"))
    } else {
      // Note, these futures will be run in parallel. This needs a better error communication model.
      for {
        jobs <- runBatch(numMaxConcurrentImport,
                         files.map(_.toPath.toAbsolutePath),
                         runImport)
        summary <- multiJobSummary(jobs, jobFilter)
      } yield summary
    }
  }

  protected def runMultiImportDataSet(
      files: Seq[File],
      maxTimeOut: Option[FiniteDuration],
      numMaxConcurrentImport: Int,
      projectName: Option[String]): Future[String] = {
    def toJob(p: Path) =
      runSingleLocalDataSetImport(p, asJson = false, maxTimeOut, projectName)
    runMultiImportXml(files, toJob, maxTimeOut, numMaxConcurrentImport)
  }

  /**
    * Get a Summary from a List of Jobs
    *
    * @param jobs List of Jobs
    * @param jobValidator Function to determine if the job (and hence the job list) was in the expected job state(s)
    * @return
    */
  protected def multiJobSummary(
      jobs: Seq[EngineJob],
      jobValidator: (EngineJob => Boolean)): Future[String] = {
    val wasSuccessful = jobs.map(jobValidator).reduce(_ && _)
    val failedJobs = jobs.filter(j => !j.isSuccessful)

    if (wasSuccessful) {
      // Should this print to stdout?
      jobs.foreach(job => logger.info(jobSummary(job, false)))
      Future.successful(s"Successfully ran ${jobs.length} import-dataset jobs")
    } else {
      failedJobs.foreach { job =>
        val summary = jobSummary(job, false)
        logger.error(summary)
        System.err.println(summary)
      }
      Future.failed(new RuntimeException(
        s"${failedJobs.length} out of ${jobs.length} import-dataset jobs failed."))
    }

  }

  private def runSingleDataSetImport(
      path: Path,
      datasetType: Option[DataSetMetaTypes.DataSetMetaType],
      asJson: Boolean,
      maxTimeOut: Option[FiniteDuration],
      projectName: Option[String]): Future[String] = {
    datasetType match {
      case Some(dsType) =>
        runSingleNonLocalDataSetImport(path.toAbsolutePath,
                                       dsType,
                                       asJson,
                                       maxTimeOut,
                                       projectName)
      case _ =>
        runSingleLocalDataSetImportWithSummary(path.toAbsolutePath,
                                               asJson,
                                               maxTimeOut,
                                               projectName)
    }
  }

  private def execImportXml(
      path: Path,
      listFiles: File => Seq[File],
      filterFile: Path => Boolean,
      doImportOne: Path => Future[String],
      doImportMany: Seq[File] => Future[String]): Future[String] = {
    val absPath = path.toAbsolutePath
    if (absPath.toFile.isDirectory) {

      val files: Seq[File] =
        if (path.toFile.isDirectory) listFiles(path.toFile).toSeq
        else Seq.empty[File]

      logger.debug(
        s"Found ${files.length} PacBio XML files from root dir $path")

      doImportMany(files)
    } else if (absPath.toFile.isFile && absPath.toString.endsWith(".fofn")) {

      logger.debug(s"Detected file of file names (FOFN) mode from $path")

      val files = Utils
        .fofnToFiles(absPath)
        .filter(filterFile)
        .map(_.toFile)

      doImportMany(files)
    } else {
      doImportOne(absPath)
    }
  }

  /**
    * Central Location to import datasets into SL
    *
    * Four cases
    *
    * 1. A single XML file local to where pbservice is executed is provided
    * 2. A FOFN of dataset XML paths is provided
    * 3. A directory is provided that is local to where pbservice is executed is provided
    * 4. A non-local import (i.e., referencing a dataset that is local to file system where the server is running
    * but not to where pbservice is exed. This requires the dataset type to be provided). The path must be an XML
    * file, not a directory.
    *
    *
    * Note, the non-local case also can not try to see if the dataset has already been imported. Whereas the
    * local case, the dataset can be read in and checked to see if was already imported (This really should be
    * encapsulated on the server side to return an previously run job?)
    *
    * @param path         Path to the DataSet XML
    * @param datasetType  DataSet metadata for non-local imports
    * @param asJson       emit the response as JSON (only when a single XML file is provided)
    * @param blockingMode Block until job is completed (failed, or successful)
    * @return A summary of the import process
    */
  def execImportDataSets(
      path: Path,
      datasetType: Option[DataSetMetaTypes.DataSetMetaType],
      asJson: Boolean = false,
      blockingMode: Boolean = true,
      numMaxConcurrentImport: Int = 5,
      projectName: Option[String] = None): Future[String] = {

    // In blocking model, set the default timeout to None
    val maxTimeOut = if (blockingMode) Some(maxTime) else None

    def doImportMany(files: Seq[File]) =
      runMultiImportDataSet(files,
                            maxTimeOut,
                            numMaxConcurrentImport,
                            projectName)
    def doImportOne(path: Path) =
      runSingleDataSetImport(path,
                             datasetType,
                             asJson,
                             maxTimeOut,
                             projectName)
    def filterFile(p: Path) = Try { getDataSetMiniMeta(p) }.isSuccess

    execImportXml(path,
                  listDataSetFiles,
                  filterFile,
                  doImportOne,
                  doImportMany)
  }

  private def convertRsMovie(path: Path,
                             name: Option[String],
                             projectId: Option[Int]): Future[EngineJob] =
    sal.convertRsMovie(path, name.getOrElse(dsNameFromRsMetadata(path)), None)

  protected def runImportRsMovie(
      path: Path,
      name: Option[String],
      asJson: Boolean = false,
      maxTimeOut: Option[FiniteDuration],
      projectName: Option[String]): Future[String] = {
    for {
      projectId <- getProjectIdByName(projectName)
      job <- convertRsMovie(path, name, projectId)
      job <- maxTimeOut
        .map(t => engineDriver(job, Some(t)))
        .getOrElse(Future.successful(job))
      dsFile <- maxTimeOut
        .map { t =>
          getDataSetResult(job.uuid, FileTypes.DS_HDF_SUBREADS)
            .map(ds => Some(ds))
        }
        .getOrElse(Future.successful(None))
    } yield importSummary(job, dsFile, asJson)
  }

  private def isMovieMetadataFile(p: Path): Boolean =
    matchRsMovieName(p.toFile) && Try { dsNameFromRsMetadata(p) }.isSuccess

  private def listMovieMetadataFiles(f: File): Array[File] = {
    f.listFiles
      .filter(_.isFile)
      .filter(fn => isMovieMetadataFile(fn.toPath))
      .toArray ++ f.listFiles
      .filter(_.isDirectory)
      .flatMap(listMovieMetadataFiles)
  }

  def runImportRsMovies(path: Path,
                        name: Option[String],
                        asJson: Boolean = false,
                        blockingMode: Boolean = false,
                        projectName: Option[String] = None,
                        numMaxConcurrentImport: Int = 5): Future[String] = {
    val maxTimeOut = if (blockingMode) Some(maxTime) else None

    def doImportOne(p: Path) =
      runImportRsMovie(p, name, asJson, maxTimeOut, projectName)

    def doImportMany(files: Seq[File], projectId: Option[Int]) = {
      if (!name.getOrElse("").isEmpty) {
        Future.failed(
          new RuntimeException(
            "--name option not allowed when path is a directory"))
      } else {
        runMultiImportXml(files,
                          (p) => convertRsMovie(p, None, projectId),
                          maxTimeOut,
                          numMaxConcurrentImport)
      }
    }

    for {
      projectId <- getProjectIdByName(projectName)
      msg <- execImportXml(path,
                           listMovieMetadataFiles,
                           isMovieMetadataFile,
                           doImportOne,
                           (f) => doImportMany(f, projectId))
    } yield msg
  }

  def runDeleteDataSet(datasetId: IdAble): Future[String] = {
    sal
      .deleteDataSet(datasetId)
      .map(response => response.message)
  }

  protected def getProjectIdByName(
      projectName: Option[String]): Future[Option[Int]] = {
    projectName
      .map { name =>
        for {
          projects <- sal.getProjects
          projectsById <- Future.successful(
            projects.map(p => (p.name, p.id)).toMap)
          projectId <- projectsById
            .get(name)
            .map { id =>
              Future.successful(Some(id))
            }
            .getOrElse {
              Future.failed(
                new RuntimeException(s"Can't find project named $name"))
            }
        } yield projectId
      }
      .getOrElse(Future.successful(None))
  }

  def runCreateProject(
      name: String,
      description: String,
      userName: Option[String],
      grantRoleToAll: Option[ProjectRequestRole.ProjectRequestRole] = None)
    : Future[String] = {
    sal
      .createProject(name, description, userName, grantRoleToAll)
      .map(project => toProjectSummary(project))
  }

  def runGetProjects: Future[String] =
    sal.getProjects
      .flatMap { projects =>
        Future.sequence(projects.map(p => sal.getProject(p.id)))
      }
      .map { projects =>
        projects.map(toProjectSummary).mkString("\n")
      }

  /**
    * Emit a template/example JSON file to supply to run-pipeline
    *
    * This uses the dev_diagnostic pipeline. Changing the reference entry point id
    * to a positive integer should
    *
    * @return
    */
  def runEmitAnalysisTemplate: Future[String] = {

    val analysisOpts = {

      // FIXME. WTF is up with this 0 usage?
      val ep = BoundServiceEntryPoint("eid_ref_dataset",
                                      DataSetMetaTypes.Reference.toString,
                                      IntIdAble(0))

      val taskOptions: Seq[ServiceTaskOptionBase] =
        Seq(
          ServiceTaskIntOption("pbsmrtpipe.task_options.test_int", 1),
          ServiceTaskBooleanOption("pbsmrtpipe.task_options.raise_exception",
                                   false),
          ServiceTaskStrOption("pbsmrtpipe.task_options.test_str",
                               "example-string")
        )

      PbsmrtpipeJobOptions(Some("My-job-name"),
                           Some("pbservice emit-analysis-template"),
                           "pbsmrtpipe.pipelines.dev_diagnostic",
                           Seq(ep),
                           taskOptions,
                           Nil)
    }

    val jx = analysisOpts.toJson.asJsObject

    val msg =
      "datasetId should be an positive integer; to obtain the datasetId from a UUID, run 'pbservice get-dataset {UUID}'. The entryId(s) can be obtained by running 'pbsmrtpipe show-pipeline-templates {PIPELINE-ID}'"
    val comment = JsObject("_comment" -> JsString(msg))

    val result = JsObject(comment.fields ++ jx.fields)
    Future.successful(result.prettyPrint)
  }

  def runShowPipelines: Future[String] = {
    sal.getPipelineTemplates
      .map(pts => pts.sortWith(_.id > _.id))
      .map(_.map(pt => s"${pt.id}: ${pt.name}").mkString("\n"))
  }

  def runAnalysisPipeline(jsonPath: Path, block: Boolean): Future[String] = {
    val jsonSrc = Source.fromFile(jsonPath.toFile).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    val analysisOptions = jsonAst.convertTo[PbsmrtpipeJobOptions]
    runAnalysisPipelineImpl(analysisOptions, block)
  }

  protected def validateEntryPoints(
      entryPoints: Seq[BoundServiceEntryPoint],
      quiet: Boolean = false): Future[Seq[DataSetMetaDataSet]] = {
    for {
      datasets <- Future.sequence(entryPoints.map { ep =>
        sal.getDataSet(ep.datasetId)
      })
    } yield datasets
  }

  protected def validatePipelineId(
      pipelineId: String): Future[PipelineTemplate] = {
    sal
      .getPipelineTemplate(pipelineId)
      .recoverWith {
        case err: Exception =>
          Future.failed(new RuntimeException(
            s"Can't find pipeline template ${pipelineId}: ${err.getMessage}\nUse 'pbsmrtpipe show-templates' to display a list of available pipelines"))
      }
  }

  protected def runAnalysisPipelineImpl(
      analysisOptions: PbsmrtpipeJobOptions,
      block: Boolean = true,
      validate: Boolean = true,
      asJson: Boolean = false): Future[String] = {
    def runValidate: Future[Option[String]] =
      if (validate) {
        for {
          _ <- validatePipelineId(analysisOptions.pipelineId)
          datasets <- validateEntryPoints(analysisOptions.entryPoints,
                                          quiet = asJson)
        } yield
          if (!asJson) {
            Some(analysisOptions.entryPoints
              .zip(datasets)
              .map {
                case (ep, ds) =>
                  val dsInfo = toDataSetInfoSummary(ds)
                  s"Found entry point ${ep.entryId} (datasetId = ${ep.datasetId})\n$dsInfo"
              }
              .mkString("\n"))
          } else None
      } else {
        Future.successful(None)
      }
    for {
      msgOrNone <- runValidate
      _ <- Future.successful(msgOrNone.map(msg => println(msg)))
      job <- sal.runAnalysisPipeline(analysisOptions)
      job <- if (block) {
        if (!asJson) println(s"Job ${job.id} UUID ${job.uuid} started")
        engineDriver(job, Some(maxTime))
      } else {
        Future.successful(job)
      }
    } yield {
      if (asJson) job.toJson.prettyPrint
      else formatJobInfo(job)
    }
  }

  protected def importEntryPoint(
      eid: String,
      xmlPath: Path): Future[BoundServiceEntryPoint] = {
    for {
      dsMeta <- Future.successful(getDataSetMiniMeta(xmlPath))
      _ <- execImportDataSets(xmlPath, Some(dsMeta.metatype))
      ds <- sal.getDataSet(dsMeta.uuid)
    } yield BoundServiceEntryPoint(eid, dsMeta.metatype.toString, ds.id)
  }

  protected def importEntryPointAutomatic(
      entryPoint: String): Future[BoundServiceEntryPoint] = {
    logger.info(s"Importing entry point '$entryPoint'")
    val epFields = entryPoint.split(':')
    if (epFields.length == 2) {
      importEntryPoint(epFields(0), Paths.get(epFields(1)))
    } else if (epFields.length == 1) {
      val xmlPath = Paths.get(epFields(0))
      val dsMeta = getDataSetMiniMeta(xmlPath)
      PbsmrtpipeConstants
        .metaTypeToEntryId(dsMeta.metatype.toString)
        .map(eid => importEntryPoint(eid, xmlPath))
        .getOrElse(Future.failed(new RuntimeException(
          s"Can't determine entryId for ${dsMeta.metatype.toString}")))
    } else {
      Future.failed(
        new RuntimeException(s"Can't interpret argument ${entryPoint}"))
    }
  }

  protected def getPipelinePresets(
      presetXml: Option[Path]): PipelineTemplatePreset =
    presetXml
      .map(path => PipelineTemplatePresetLoader.loadFrom(path))
      .getOrElse(defaultPresets)

  private def validateEntryPointIds(
      entryPoints: Seq[BoundServiceEntryPoint],
      pipeline: PipelineTemplate): Future[Seq[BoundServiceEntryPoint]] = {
    val eidsInput = entryPoints.map(_.entryId).sorted.mkString(", ")
    val eidsTemplate =
      pipeline.entryPoints.map(_.entryId).sorted.mkString(", ")
    if (eidsInput != eidsTemplate) {
      Future.failed(
        new RuntimeException(
          "Mismatch between supplied and expected entry points: the input " +
            s"datasets correspond to entry points ($eidsInput), while the " +
            s"pipeline ${pipeline.id} requires entry points ($eidsTemplate)"))
    } else {
      Future.successful(entryPoints)
    }
  }

  // XXX there is a bit of a disconnect between how preset.xml is handled and
  // how options are actually passed to services, so we need to convert them
  // here
  protected def getPipelineServiceOptions(
      jobTitle: String,
      pipelineId: String,
      entryPoints: Seq[BoundServiceEntryPoint],
      presets: PipelineTemplatePreset,
      userTaskOptions: Option[Map[String, String]] = None)
    : Future[PbsmrtpipeJobOptions] = {
    val workflowOptions = Seq[ServiceTaskOptionBase]()
    val userOptions: Seq[ServiceTaskOptionBase] = presets.taskOptions ++
      userTaskOptions
        .getOrElse(Map[String, String]())
        .map {
          case (k, v) => k -> ServiceTaskStrOption(k, v)
        }
        .values
    logger.debug("Getting pipeline options from server")
    for {
      pipeline <- sal.getPipelineTemplate(pipelineId)
      eps <- validateEntryPointIds(entryPoints, pipeline)
      taskOptions <- Future.successful {
        PipelineUtils.getPresetTaskOptions(pipeline, userOptions)
      }
    } yield
      PbsmrtpipeJobOptions(Some(jobTitle),
                           Some("from pbservice"),
                           pipelineId,
                           entryPoints,
                           taskOptions,
                           workflowOptions)
  }

  def runPipeline(pipelineId: String,
                  entryPoints: Seq[String],
                  jobTitle: String,
                  presetXml: Option[Path] = None,
                  block: Boolean = true,
                  validate: Boolean = true,
                  taskOptions: Option[Map[String, String]] = None,
                  asJson: Boolean = false): Future[String] = {
    if (entryPoints.isEmpty)
      return Future.failed(
        new RuntimeException("At least one entry point is required"))

    val pipelineIdFull =
      if (pipelineId.split('.').length != 3)
        s"pbsmrtpipe.pipelines.$pipelineId"
      else pipelineId

    logger.info(s"pipeline ID: $pipelineIdFull")
    var jobTitleTmp = jobTitle
    if (jobTitle.length == 0) jobTitleTmp = s"pbservice-$pipelineIdFull"
    for {
      _ <- validatePipelineId(pipelineIdFull)
      eps <- Future.sequence(entryPoints.map(importEntryPointAutomatic))
      presets <- Future.successful(getPipelinePresets(presetXml))
      opts <- getPipelineServiceOptions(jobTitleTmp,
                                        pipelineIdFull,
                                        eps,
                                        presets,
                                        taskOptions)
      job <- sal.runAnalysisPipeline(opts)
      job <- if (block) {
        engineDriver(job, Some(maxTime))
      } else {
        Future.successful(job)
      }
    } yield formatJobInfo(job, asJson)
  }

  def runTerminateAnalysisJob(jobId: IdAble): Future[String] = {
    def toSummary(m: MessageResponse) = m.message
    println(s"Attempting to terminate Analysis Job ${jobId.toIdString}")
    for {
      job <- sal.getJob(jobId)
      messageResponse <- sal.terminatePbsmrtpipeJob(job.id)
    } yield toSummary(messageResponse)
  }

  def runDeleteJob(jobId: IdAble, force: Boolean = true): Future[String] = {
    def deleteJob(job: EngineJob, nChildren: Int): Future[EngineJob] = {
      val WARN_TERM_FAILED =
        "Job termination failed; will delete anyway, but this may have unpredictable side effects"
      val ERR_NOT_COMPLETE =
        s"Can't delete this job because it hasn't completed - try 'pbservice terminate-job ${jobId.toIdString} ...' first, or add the argument --force if you are absolutely certain the job is okay to delete"
      val WARN_CHILDREN =
        s"WARNING: job output was used by $nChildren active jobs - deleting it may have unintended side effects"
      val ERR_CHILDREN =
        s"Can't delete job ${job.id} because ${nChildren} active jobs used its results as input; add --force if you are absolutely certain the job is okay to delete"
      def terminateJob() = runTerminateAnalysisJob(jobId).recover {
        case e: Exception => println(WARN_TERM_FAILED)
      }
      def fx: Future[Any] =
        if (!job.isComplete) {
          if (force) {
            System.err.println(
              "WARNING: job did not complete - attempting to terminate")
            terminateJob()
          } else {
            Future.failed(new RuntimeException(ERR_NOT_COMPLETE))
          }
        } else if (nChildren > 0) {
          if (force) {
            System.err.println(WARN_CHILDREN)
            Thread.sleep(5000)
            Future.successful(None)
          } else {
            Future.failed(new RuntimeException(ERR_CHILDREN))
          }
        } else {
          Future.successful(None)
        }
      for {
        _ <- fx
        deleteJob <- sal.deleteJob(job.uuid, force = force)
      } yield deleteJob
    }
    println(s"Attempting to delete job ${jobId.toIdString}")
    for {
      job <- sal.getJob(jobId)
      children <- sal.getJobChildren(job.uuid)
      deleteJob <- deleteJob(job, children.size)
      _ <- engineDriver(deleteJob, Some(maxTime))
    } yield s"Job ${jobId.toIdString} deleted."
  }

  /**
    * Run export of a single job, returning a Path to the zip file
    */
  protected def runExportJobImpl(jobId: IdAble,
                                 destPath: Path,
                                 includeEntryPoints: Boolean): Future[Path] = {
    for {
      job <- sal.getJob(jobId)
      exportJob <- sal.exportJobs(
        Seq(job.id),
        destPath,
        includeEntryPoints,
        Some(s"pbservice-export-job-$jobId"),
        Some(s"Run from 'pbservice export-job $jobId'"))
      _ <- engineDriver(exportJob, Some(maxTime))
      ds <- sal.getJobDataStore(exportJob.id)
      path <- ds
        .find(_.fileTypeId == FileTypes.ZIP.fileTypeId)
        .map(f => Future.successful(Paths.get(f.path)))
        .getOrElse(Future.failed(new RuntimeException(
          s"Can't get ZIP file from export job datastore")))
    } yield path
  }

  def runExportJob(jobId: IdAble,
                   destPath: Path,
                   includeEntryPoints: Boolean): Future[String] = {
    val actualDestPath = Option(destPath).getOrElse {
      val defaultPath = Paths.get("").toAbsolutePath
      val msg =
        s"No output path supplied, defaulting to current working directory ($defaultPath); this will fail if SMRT Link is unable to write here"
      logger.warn(msg)
      println(msg)
      defaultPath
    }
    for {
      p <- runExportJobImpl(jobId, actualDestPath, includeEntryPoints)
    } yield s"Job exported to ${p.toString}"
  }

  def runImportJob(path: Path): Future[String] = {
    for {
      job <- sal.importJob(path)
      _ <- engineDriver(job, Some(maxTime))
      children <- sal.getJobChildren(job.id)
      imported <- children.headOption.map { childJob =>
        Future.successful(childJob)
      } getOrElse {
        Future.failed(new RuntimeException("No job children found"))
      }
    } yield
      s"Job ${imported.uuid} ('${imported.name}') imported with ID ${imported.id}"
  }

  def runUpdateJob(jobId: IdAble,
                   name: Option[String],
                   comment: Option[String],
                   tags: Option[String],
                   asJson: Boolean = false): Future[String] =
    sal
      .updateJob(jobId, name, comment, tags)
      .map(job => formatJobInfo(job, asJson))

  protected def manifestSummary(m: PacBioComponentManifest) =
    s"Component name:${m.name} id:${m.id} version:${m.version}"

  protected def manifestsSummary(
      manifests: Seq[PacBioComponentManifest]): String = {
    val headers = Seq("id", "version", "name")
    val table =
      manifests.sortWith(_.id < _.id).map(m => Seq(m.id, m.version, m.name))
    toTable(table, headers)
  }

  def runGetPacBioManifests(): Future[String] =
    sal.getPacBioComponentManifests.map(manifestsSummary)

  // This is to make it backward compatiblity. Remove this when the other systems are updated
  private def getManifestById(
      manifestId: String): Future[PacBioComponentManifest] = {
    sal.getPacBioComponentManifests.flatMap { manifests =>
      manifests.find(x => x.id == manifestId) match {
        case Some(m) => Future { m }
        case _ =>
          Future.failed(
            new ResourceNotFoundError(s"Unable to find $manifestId"))
      }
    }
  }

  def runGetPacBioManifestById(ix: String): Future[String] =
    getManifestById(ix).map(manifestSummary)

  protected def pacBioDataBundlesSummary(
      bundles: Seq[PacBioDataBundle]): String = {
    val headers: Seq[String] =
      Seq("Bundle Id", "Version", "Imported At", "Is Active")
    val table = bundles.map(b =>
      Seq(b.typeId, b.version, b.importedAt.toString(), b.isActive.toString))
    toTable(table, headers)
  }

  def runGetPacBioDataBundles: Future[String] =
    sal.getPacBioDataBundles().map(pacBioDataBundlesSummary)

  def runTsSystemStatus(user: String, comment: String): Future[String] = {
    def toSummary(job: EngineJob) =
      s"Tech support bundle sent.\nUser = $user\nComments: $comment"
    println(s"Attempting to send tech support status bundle")
    for {
      job <- sal.runTsSystemStatus(user, comment)
      _ <- engineDriver(job, Some(maxTime))
    } yield toSummary(job)
  }

  def runTsJobBundle(jobId: IdAble,
                     user: String,
                     comment: String): Future[String] = {
    def toSummary(job: EngineJob) =
      s"Tech support job bundle sent.\nJob = ${job.id}; name = ${job.name}\nUser = $user\nComments: $comment"
    println(s"Attempting to send tech support failed job bundle")
    for {
      failedJob <- sal.getJob(jobId)
      job <- sal.runTsJobBundle(failedJob.id, user, comment)
      _ <- engineDriver(job, Some(maxTime))
    } yield toSummary(failedJob)
  }

  protected def alarmsSummary(alarms: Seq[AlarmStatus]): String = {
    val headers: Seq[String] =
      Seq("Id", "Severity", "Updated At", "Value", "Message")
    val table = alarms.map(
      a =>
        Seq(a.id,
            a.severity.toString,
            a.updatedAt.toString(),
            a.value.toString,
            a.message.getOrElse("")))
    toTable(table, headers)
  }

  def runGetAlarms: Future[String] = sal.getAlarms().map(alarmsSummary)

  def runImportRun(xmlFile: Path,
                   reserved: Boolean = false,
                   asJson: Boolean = true): Future[String] = {
    for {
      runSummary <- sal.createRun(FileUtils.readFileToString(xmlFile.toFile))
      runSummary <- if (reserved) {
        sal.updateRun(runSummary.uniqueId, reserved = Some(true))
      } else Future.successful(runSummary)
    } yield toRunSummary(runSummary.withDataModel(""), asJson)
  }

  def runGetRun(runId: UUID,
                asJson: Boolean = false,
                asXml: Boolean = false): Future[String] =
    sal.getRun(runId).map(run => toRunSummary(run, asJson, asXml))
}

object PbService
    extends ClientAppUtils
    with SmrtLinkClientProvider
    with LazyLogging {
  import com.pacbio.secondary.smrtlink.jsonprotocols.ConfigModelsJsonProtocol._

  def apply(c: PbServiceParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")

    try {
      val fx: Future[String] =
        getClient(c.host,
                  c.port,
                  Some(c.user),
                  c.password,
                  c.authToken,
                  c.usePassword)(actorSystem)
          .flatMap { sal =>
            val ps = new PbService(sal, c.maxTime)
            c.mode match {
              case Modes.STATUS => ps.exeStatus(c.asJson)
              case Modes.IMPORT_DS =>
                ps.execImportDataSets(c.path,
                                      c.nonLocal,
                                      c.asJson,
                                      c.blockImportDataSet,
                                      c.numMaxConcurrentImport,
                                      c.project)
              case Modes.IMPORT_FASTA =>
                ps.runImportFasta(c.path,
                                  c.getName,
                                  c.organism,
                                  c.ploidy,
                                  c.project)
              case Modes.IMPORT_FASTA_GMAP =>
                ps.runImportFastaGmap(c.path,
                                      c.getName,
                                      c.organism,
                                      c.ploidy,
                                      c.project)
              case Modes.IMPORT_BARCODES =>
                ps.runImportBarcodes(c.path, c.getName, c.project)
              case Modes.IMPORT_MOVIE =>
                ps.runImportRsMovies(c.path,
                                     c.name,
                                     c.asJson,
                                     c.block,
                                     c.project,
                                     c.numMaxConcurrentImport)
              case Modes.ANALYSIS => ps.runAnalysisPipeline(c.path, c.block)
              case Modes.TEMPLATE => ps.runEmitAnalysisTemplate
              case Modes.PIPELINE =>
                ps.runPipeline(c.pipelineId,
                               c.entryPoints,
                               c.jobTitle,
                               c.presetXml,
                               c.block,
                               taskOptions = c.taskOptions,
                               asJson = c.asJson)
              case Modes.SHOW_PIPELINES => ps.runShowPipelines
              case Modes.JOB =>
                ps.runGetJobInfo(c.jobId,
                                 c.asJson,
                                 c.dumpJobSettings,
                                 c.showReports,
                                 c.showFiles)
              case Modes.JOBS =>
                ps.runGetJobs(c.maxItems,
                              c.asJson,
                              c.jobType,
                              c.jobState,
                              c.searchName,
                              c.searchSubJobType)
              case Modes.TERMINATE_JOB => ps.runTerminateAnalysisJob(c.jobId)
              case Modes.DELETE_JOB => ps.runDeleteJob(c.jobId, c.force)
              case Modes.EXPORT_JOB =>
                ps.runExportJob(c.jobId, c.path, c.includeEntryPoints)
              case Modes.IMPORT_JOB => ps.runImportJob(c.path)
              case Modes.UPDATE_JOB =>
                ps.runUpdateJob(c.jobId, c.name, c.comment, c.tags, c.asJson)
              case Modes.DATASET => ps.runGetDataSetInfo(c.datasetId, c.asJson)
              case Modes.DATASETS =>
                ps.runGetDataSets(c.datasetType,
                                  c.maxItems,
                                  c.asJson,
                                  c.searchName,
                                  c.searchPath)
              case Modes.DELETE_DATASET => ps.runDeleteDataSet(c.datasetId)
              case Modes.MANIFEST => ps.runGetPacBioManifestById(c.manifestId)
              case Modes.MANIFESTS => ps.runGetPacBioManifests
              case Modes.BUNDLES => ps.runGetPacBioDataBundles
              case Modes.TS_STATUS =>
                ps.runTsSystemStatus(c.user, c.getComment)
              case Modes.TS_JOB =>
                ps.runTsJobBundle(c.jobId, c.user, c.getComment)
              case Modes.ALARMS => ps.runGetAlarms
              case Modes.CREATE_PROJECT =>
                ps.runCreateProject(c.getName,
                                    c.description,
                                    Some(c.user),
                                    c.grantRoleToAll)
              case Modes.GET_PROJECTS => ps.runGetProjects
              case Modes.IMPORT_RUN =>
                ps.runImportRun(c.path, c.reserved, c.asJson)
              case Modes.GET_RUN => ps.runGetRun(c.runId, c.asJson, c.asXml)
            }
          }
      executeBlockAndSummary(fx, c.maxTime)
    } finally {
      actorSystem.terminate()
    }
  }
}

object PbServiceApp extends App with LazyLogging {
  def run(args: Seq[String]) = {
    val xc = PbServiceParser.parser
      .parse(args.toSeq, PbServiceParser.defaults) match {
      case Some(config) =>
        logger.debug(s"Args $config")
        PbService(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
