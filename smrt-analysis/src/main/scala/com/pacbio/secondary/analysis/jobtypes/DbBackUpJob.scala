package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.Path
import java.util.UUID

import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, BaseCoreJob, BaseJobOptions, JobResultWriter}
import com.pacbio.secondary.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils
import com.pacbio.secondary.analysis.reports.ReportModels._
import com.pacbio.secondary.analysis.reports.ReportUtils
import com.pacbio.secondary.analysis.tools.timeUtils


case class DbBackUpJobOptions(rootBackUpDir: Path,
                              baseBackUpName: String = "smrtlink-db-backup",
                              maxNumBackups: Int = 5,
                              override val projectId: Int = GENERAL_PROJECT_ID) extends BaseJobOptions {

  def toJob = new DbBackUpJob(this)

}

class DbBackUpJob(opts: DbBackUpJobOptions) extends BaseCoreJob(opts: DbBackUpJobOptions) with timeUtils {

  type Out = PacBioDataStore
  override val jobTypeId = JobTypeId("smrtlink_db_backup")


  /**
    * Create Report that has basic metadata of the db backup process
    *
    * @param name Name of the database backup
    * @param backUpSizeMB Size of the Backup in MB
    * @param message Comment or Description of the db backup process
    * @return
    */
  def generateReport(name: String, backUpSizeMB: Int, message: String, backUpTimeSec: Double): Report = {

    val a1 = ReportStrAttribute("", "", "")
    val a2 = ReportBooleanAttribute("", "", value = true)
    val a3 = ReportDoubleAttribute("", "", value = backUpTimeSec)

    val attributes: List[ReportAttribute] = List(a1, a2, a3)

    Report("smrtlink_db_backup", "SMRT Link DB Backup",
      attributes = attributes, uuid = UUID.randomUUID(),
      plotGroups = Nil, tables = Nil)
  }

  def toReportDataStoreFile(uuid: UUID, path: Path): DataStoreFile = {

    val now = JodaDateTime.now()
    val name = "Name"
    val description = "Description"

    DataStoreFile(uuid, "source-id", FileTypes.REPORT.fileTypeId, path.toFile.length(), now, now, path.toAbsolutePath.toString, isChunked = false, name, description)
  }

  def backUpCmd(output: Path, dbName: String, port: Int, user: String, exe: String = "pg_dumpall"): Seq[String] =
    Seq(exe, "-F", "t", s"--file=${output.toAbsolutePath}", s"--database=$dbName", s"--port=$port", s"--username=$user", "--no-password", "--verbose")

  // PGPASSWORD=my-password pg_dumpall -F t -f --database={database-name} --port={port} --username={user-name} --no-password --verbose
  def runBackUp(output: Path, dbName: String, port: Int, user: String, exe: String = "pg_dumpall", stdout: Path, stderr: Path) = {
    val cmd = backUpCmd(output, dbName, port, user, exe)
    ExternalToolsUtils.runCmd(cmd, stdout, stderr)
  }

  def deleteMaxBackups(rootDir: Path, maxBackUps: Int): Seq[Path] = {

    Nil
  }

  override def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    val createdAt = JodaDateTime.now()
    resultsWriter.writeLineStdout(s"Starting Db Backup with Options $opts")

    val outputDs = job.path.resolve("datastore.json")
    val reportPath = job.path.resolve("smrtlink_db_backup_report.json")

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logDsFile = toMasterDataStoreFile(logPath, s"Job Master log of ${jobTypeId.id}")

    val timeStamp = createdAt.formatted("YYYY-MM-DD")
    val name = s"${opts.baseBackUpName}-$timeStamp.tar"

    val backUpPath = opts.rootBackUpDir.resolve(name)


    val backUpSizeMB = (backUpPath.toFile.length() / 1024.0 / 1024.0).toInt
    val backUpTimeSec = 1.1

    val report = generateReport(name, backUpSizeMB, "Successful BackUp DB", backUpTimeSec)

    ReportUtils.writeReport(report, reportPath)

    val reportDsFile = toReportDataStoreFile(report.uuid, reportPath)

    val ds = PacBioDataStore(createdAt, createdAt, "0.2.0", Seq(reportDsFile, logDsFile))
    FileUtils.writeStringToFile(outputDs.toFile, ds.toJson.prettyPrint)


    Left(ResultFailed(job.jobId, jobTypeId.toString, "NOT SUPPORTED", 1, AnalysisJobStates.FAILED, host))
  }

}
