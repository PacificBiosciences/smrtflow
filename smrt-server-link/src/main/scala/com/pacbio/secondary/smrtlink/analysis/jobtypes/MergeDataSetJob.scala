package com.pacbio.secondary.smrtlink.analysis.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetLoader,
  DataSetMerger,
  DataSetWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacificbiosciences.pacbiodatasets._

// Merge DataSets
case class MergeDataSetOptions(
    datasetType: String,
    paths: Seq[Path],
    name: String,
    override val projectId: Int = GENERAL_PROJECT_ID)
    extends BaseJobOptions {
  def toJob = new MergeDataSetJob(this)

  override def validate = {
    for {
      v1 <- Validators.filesExists(paths)
      v2 <- Validators.validateDataSetType(datasetType)
    } yield v2
  }
}

/**
  * Simple Merge DataSet Job
  *
  *
  * Created by mkocher on 5/1/15.
  */
class MergeDataSetJob(opts: MergeDataSetOptions)
    extends BaseCoreJob(opts: MergeDataSetOptions)
    with MockJobUtils
    with timeUtils {

  type Out = PacBioDataStore
  // Note, this is inconsistent with the id defined in JobTypeIds "merge-datasets"
  // Changing this will break backward compatibility with the source id in the datastore
  val jobTypeId = JobTypeIds.MERGE_DATASETS

  def run(job: JobResourceBase,
          resultsWriter: JobResultsWriter): Either[ResultFailed, Out] = {

    val startedAt = JodaDateTime.now()

    resultsWriter.writeLine(
      s"Starting dataset merging of ${opts.paths.length} ${opts.datasetType} Files at ${startedAt.toString}")

    resultsWriter.writeLine(s"DataSet Merging options: $opts")
    opts.paths.foreach(x => resultsWriter.writeLine(s"File ${x.toString}"))

    val outputPath = job.path.resolve("merged.dataset.xml")
    val datastoreJson = job.path.resolve("datastore.json")

    def toDF[T <: DataSetType](ds: T): DataStoreFile = {
      val uuid = UUID.fromString(ds.getUniqueId)
      val createdAt = JodaDateTime.now()
      val modifiedAt = createdAt
      DataStoreFile(
        uuid,
        s"pbscala::merge_dataset",
        ds.getMetaType,
        outputPath.toFile.length,
        createdAt,
        modifiedAt,
        outputPath.toAbsolutePath.toString,
        isChunked = false,
        Option(ds.getName).getOrElse("PacBio DataSet"),
        s"Merged PacBio DataSet from ${opts.paths.length} files"
      )
    }

    val mType = DataSetMetaTypes.toDataSetType(opts.datasetType)

    val result = mType match {
      case Some(DataSetMetaTypes.Subread) =>
        Some(
          (DataSetMerger
             .mergeSubreadSetPathsTo(opts.paths, opts.name, outputPath),
           DataSetMetaTypes.Subread))
      case Some(DataSetMetaTypes.HdfSubread) =>
        Some(
          (DataSetMerger
             .mergeHdfSubreadSetPathsTo(opts.paths, opts.name, outputPath),
           DataSetMetaTypes.HdfSubread))
      case Some(DataSetMetaTypes.Alignment) =>
        Some(
          (DataSetMerger
             .mergeAlignmentSetPathsTo(opts.paths, opts.name, outputPath),
           DataSetMetaTypes.Alignment))
      case x =>
        resultsWriter.writeLineError(s"Unsupported DataSet type $x")
        None
    }

    result match {
      case Some((dataset, dst)) =>
        resultsWriter.write(
          s"Successfully merged datasets to ${outputPath.toAbsolutePath}")
        val dataStoreFile = toDF(dataset)
        val now = JodaDateTime.now()

        val logPath = job.path.resolve(JobConstants.JOB_STDOUT)

        val reportFiles = DataSetReports.runAll(outputPath,
                                                dst,
                                                job.path,
                                                jobTypeId,
                                                resultsWriter)

        val logFile =
          toSmrtLinkJobLog(
            logPath,
            Some(
              s"${JobConstants.DATASTORE_FILE_MASTER_DESC} of the Merge Dataset job"))

        // FIX hardcoded version
        val ds = PacBioDataStore(now,
                                 now,
                                 "0.2.1",
                                 Seq(dataStoreFile, logFile) ++ reportFiles)
        writeDataStore(ds, datastoreJson)
        resultsWriter.write(
          s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
        Right(ds)
      case _ =>
        Left(
          ResultFailed(
            job.jobId,
            jobTypeId.id,
            s"Failed to merge datasets. Unsupported dataset type '${opts.datasetType}'",
            computeTimeDeltaFromNow(startedAt),
            AnalysisJobStates.FAILED,
            host
          ))
    }
  }
}
