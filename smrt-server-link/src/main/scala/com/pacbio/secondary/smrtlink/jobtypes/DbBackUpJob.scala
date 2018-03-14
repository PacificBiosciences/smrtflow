package com.pacbio.secondary.smrtlink.jobtypes

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.util.{Failure, Success, Try}
import collection.JavaConverters._
import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat
import spray.json._

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.ExternalToolsUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPointRecord

case class DbBackUpJobOptions(user: String,
                              comment: String,
                              name: Option[String],
                              description: Option[String],
                              projectId: Option[Int] = Some(
                                JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.DB_BACKUP
  override def toJob() = new DbBackUpJob(this)

  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] =
    Seq.empty[EngineJobEntryPointRecord]

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    config.rootDbBackUp match {
      case Some(_) => None
      case _ =>
        Some(InvalidJobOptionError(
          "Unable to backup database. System is not configured with a DB Backup dir."))
    }
  }

}

trait DbBackUpBase extends CoreJobUtils with timeUtils {
  val JOB_TYPE_ID_STR = JobTypeIds.DB_BACKUP.id
  val BASE_BACKUP_NAME = "smrtlink-db-backup"
  val BACKUP_EXT = "_db.bak"
  val MAX_NUM_BACKUPS = 5

  /**
    * Create Report that has basic metadata of the db backup process.
    *
    * The Report has attributes
    * - created at date
    * - was successful
    * - backup size in MB
    * - name of backup
    * - comment
    *
    * @param name Name of the database backup
    * @param backUpSizeMB Size of the Backup in MB
    * @param comment Comment or Description of the db backup process
    * @return
    */
  def generateReport(name: String,
                     backUpSizeMB: Int,
                     comment: String,
                     backUpTimeSec: Double): Report = {

    val a1 = ReportStrAttribute("db_backup_name", "DB BackUp Name", name)
    val a2 =
      ReportBooleanAttribute("was_successful", "Was Successful", value = true)
    val a3 = ReportDoubleAttribute("db_backup_size_mb",
                                   "Size (MB)",
                                   value = backUpTimeSec)
    val a4 = ReportStrAttribute("comment", "Comment", value = comment)

    val attributes: List[ReportAttribute] = List(a1, a2, a3, a4)

    Report("smrtlink_db_backup",
           "SMRT Link DB Backup",
           attributes = attributes,
           uuid = UUID.randomUUID(),
           plotGroups = Nil,
           tables = Nil)
  }

  def toReportDataStoreFile(uuid: UUID, path: Path): DataStoreFile = {

    val now = JodaDateTime.now()
    val name = "SL BackUp Report"
    val description = "SMRT Link DataBase Backup Report"

    DataStoreFile(uuid,
                  "source-id",
                  FileTypes.REPORT.fileTypeId,
                  path.toFile.length(),
                  now,
                  now,
                  path.toAbsolutePath.toString,
                  isChunked = false,
                  name,
                  description)
  }

  // For pg_dump use -F t to write to tar
  // env PGPASSWORD=my-password pg_dumpall --file={output-file} --database={database-name} --port={port} \
  // --username={user-name} --no-password --verbose
  def backUpCmd(output: Path,
                dbName: String,
                port: Int,
                user: String,
                exe: String = "pg_dumpall"): Seq[String] =
    Seq(exe,
        s"--file=${output.toAbsolutePath}",
        s"--database=$dbName",
        s"--port=$port",
        s"--username=$user",
        "--no-password",
        "--verbose")

  def runBackUp(output: Path,
                dbName: String,
                port: Int,
                user: String,
                password: String,
                stdout: Path,
                stderr: Path,
                exe: String = "pg_dumpall"): Try[String] = {
    val cmd = backUpCmd(output, dbName, port, user, exe)
    val extraEnv = Map("PGPASSWORD" -> password)
    ExternalToolsUtils.runUnixCmd(cmd, stdout, stderr, Some(extraEnv)) match {
      case Tuple2(0, _) => Success("Completed backup")
      case Tuple2(exitCode, message) =>
        Failure(
          new Exception(
            s"Failed to run Command with exit code $exitCode $message"))
    }
  }

  /**
    * Returns a list of backups that were deleted.
    *
    * @param rootDir Root Directory of db backups
    * @param maxBackUps Max Number of Database backups
    * @return
    */
  def deleteMaxBackups(rootDir: Path,
                       maxBackUps: Int,
                       backupExt: String): Seq[Path] = {

    def deleteBackUp(file: File): File = {
      logger.info(s"Deleting DB backup $file")
      Files.delete(file.toPath)
      logger.info(s"Successfully deleted DB backup $file")
      file
    }

    val allFiles: Seq[File] =
      if (rootDir.toFile.isDirectory) rootDir.toAbsolutePath.toFile.listFiles()
      else Seq.empty[File]

    val allBackUps = allFiles
      .filter(_.isFile)
      .filter(_.getName.endsWith(backupExt))
      .sortBy(f => f.lastModified())

    val backUpsToDelete: Seq[File] =
      if (allBackUps.length > maxBackUps) allBackUps.take(maxBackUps)
      else Seq.empty[File]

    val deletedFiles = backUpsToDelete.map(deleteBackUp)

    deletedFiles.toList.map(_.toPath)
  }

  /**
    * Generate a Report and DataStore from the results of Database Backup
    */
  def postProcess(outputDataStore: Path,
                  createdAt: JodaDateTime,
                  dbBackUpPath: Path,
                  reportPath: Path,
                  message: String,
                  dataStoreFiles: Seq[DataStoreFile]): PacBioDataStore = {

    val backUpSizeMB = (dbBackUpPath.toFile.length() / 1024.0 / 1024.0).toInt
    val backUpTimeSec = computeTimeDelta(JodaDateTime.now(), createdAt)

    val name = dbBackUpPath.getFileName.toString
    val report = generateReport(name, backUpSizeMB, message, backUpTimeSec)

    ReportUtils.writeReport(report, reportPath)

    val reportDsFile = toReportDataStoreFile(report.uuid, reportPath)

    val dsFiles = Seq(reportDsFile) ++ dataStoreFiles

    val ds = PacBioDataStore(createdAt, createdAt, "0.2.0", dsFiles)
    writeDataStore(ds, outputDataStore)
    ds
  }

  def runCoreJob(resources: JobResourceBase,
                 resultsWriter: JobResultsWriter,
                 rootBackUpDir: Path,
                 dbName: String,
                 dbPort: Int,
                 dbUser: String,
                 dbPasswd: String,
                 jobHost: String,
                 logFile: DataStoreFile) = {
    val createdAt = JodaDateTime.now()

    val outputDs = resources.path.resolve("datastore.json")
    val reportPath = resources.path.resolve("smrtlink_db_backup_report.json")

    val pattern = "yyyy_MM_dd-HH_mm_ss"
    val formatter = DateTimeFormat.forPattern(pattern)
    val timeStamp = formatter.print(createdAt)
    val name = s"${BASE_BACKUP_NAME}-$timeStamp${BACKUP_EXT}"
    val backUpPath = rootBackUpDir.resolve(name)

    val tx = for {
      // Should these have separate files for stdout and stderr?
      message <- runBackUp(
        backUpPath,
        dbName = dbName,
        port = dbPort,
        user = dbUser,
        password = dbPasswd,
        stdout = Paths.get(logFile.path),
        stderr = Paths.get(logFile.path)
      )
      datastore <- Try {
        postProcess(outputDs,
                    createdAt,
                    backUpPath,
                    reportPath,
                    message,
                    Seq(logFile))
      }
      deletedBackups <- Try {
        deleteMaxBackups(rootBackUpDir, MAX_NUM_BACKUPS, BACKUP_EXT)
      }
    } yield (datastore, deletedBackups)

    tx match {
      case Success((ds, deletedBackups)) =>
        resultsWriter.writeLine(s"Deleted ${deletedBackups.length}")
        deletedBackups.foreach { f =>
          resultsWriter.writeLine(s"Deleted $f")
        }
        Right(ds)
      case Failure(ex) =>
        val runTimeSec = computeTimeDelta(JodaDateTime.now(), createdAt)
        Left(
          ResultFailed(resources.jobId,
                       JOB_TYPE_ID_STR,
                       s"Failed backup ${ex.getMessage}",
                       runTimeSec,
                       AnalysisJobStates.FAILED,
                       jobHost))
    }
  }
}

class DbBackUpJob(opts: DbBackUpJobOptions)
    extends ServiceCoreJob(opts)
    with DbBackUpBase {
  type Out = PacBioDataStore

  override def run(resources: JobResourceBase,
                   resultsWriter: JobResultsWriter,
                   dao: JobsDao,
                   config: SystemJobConfig) = {
    // SHIM
    // Assume that validate has already been called.
    val rootBackUp = config.rootDbBackUp.get
    val logFile = getStdOutLog(resources, dao)
    resultsWriter.writeLine(s"Starting Db Backup with Options $opts")
    runCoreJob(
      resources,
      resultsWriter,
      rootBackUp,
      dbName = config.dbConfig.dbName,
      dbPort = config.dbConfig.port,
      dbUser = config.dbConfig.username,
      dbPasswd = config.dbConfig.password,
      jobHost = host,
      logFile = logFile
    )
  }
}
