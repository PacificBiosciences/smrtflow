package com.pacbio.secondary.smrtlink.analysis.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacificbiosciences.pacbiodatasets._

case class ExportDataSetsOptions(datasetType: DataSetMetaTypes.DataSetMetaType,
                                 paths: Seq[Path],
                                 outputPath: Path,
                                 override val projectId: Int =
                                   GENERAL_PROJECT_ID)
    extends BaseJobOptions {
  def toJob = new ExportDataSetsJob(this)

  override def validate = {
    for {
      v1 <- Validators.filesExists(paths)
      v2 <- Validators.validateDataSetType(datasetType.dsId)
    } yield v2
  }
}

class ExportDataSetsJob(opts: ExportDataSetsOptions)
    extends BaseCoreJob(opts: ExportDataSetsOptions)
    with MockJobUtils
    with timeUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeIds.EXPORT_DATASETS

  def run(job: JobResourceBase,
          resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    val startedAt = JodaDateTime.now()

    resultsWriter.writeLine(
      s"Starting export of ${opts.paths.length} ${opts.datasetType} Files at ${startedAt.toString}")

    resultsWriter.writeLine(s"DataSet Export options: $opts")
    opts.paths.foreach(x => resultsWriter.writeLine(s"File ${x.toString}"))

    val datastoreJson = job.path.resolve("datastore.json")

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toMasterDataStoreFile(
      logPath,
      "Log file of the details of the Export DataSet Job job")

    val nbytes = ExportDataSets(opts.paths, opts.datasetType, opts.outputPath)
    resultsWriter.write(
      s"Successfully exported datasets to ${opts.outputPath.toAbsolutePath}")
    val now = JodaDateTime.now()
    val dataStoreFile = DataStoreFile(
      UUID.randomUUID(),
      s"pbscala::${jobTypeId.id}",
      FileTypes.ZIP.fileTypeId,
      opts.outputPath.toFile.length,
      now,
      now,
      opts.outputPath.toAbsolutePath.toString,
      isChunked = false,
      "ZIP file",
      s"ZIP file containing ${opts.paths.length} datasets"
    )

    val ds = PacBioDataStore(now, now, "0.2.1", Seq(dataStoreFile, logFile))
    writeDataStore(ds, datastoreJson)
    resultsWriter.write(
      s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
    Right(ds)
  }
}
