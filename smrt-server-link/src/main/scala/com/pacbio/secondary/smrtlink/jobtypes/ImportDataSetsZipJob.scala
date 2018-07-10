package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}

import util.Try
import org.joda.time.{DateTime => JodaDateTime}

import better.files._
import File._
import java.io.{File => JFile}

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.io.ModelIOUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

/**
  *
  * @param path        Path to ZIP DataSet XML(s)
  * @param name        Name of the Job
  * @param description Description of the Job
  */
case class ImportDataSetsZipJobOptions(
    path: Path,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB),
    tags: Option[String] = None)
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.IMPORT_DATASETS_ZIP
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = None
  override def toJob() = new ImportDataSetsZipJob(this)
}

class ImportDataSetsZipJob(opts: ImportDataSetsZipJobOptions)
    extends ServiceCoreJob(opts)
    with CoreJobUtils
    with timeUtils
    with ModelIOUtils {
  type Out = PacBioDataStore

  private def unzipTo(zipPath: Path, destination: Path): Path = {
    val zipFile: File = File(zipPath.toAbsolutePath.toString)

    zipFile.unzipTo(destination = File(destination.toAbsolutePath.toString))

    // The interface of a ZIP'ed DataSet(s) XML requires a datastore.json in the root dir.
    destination.resolve(JobConstants.OUTPUT_DATASTORE_JSON)
  }

  private def runReports(
      dataStoreFiles: Seq[DataStoreFile],
      rootReportDir: Path,
      resultsWriter: JobResultsWriter): Seq[DataStoreFile] = {

    def toFileMeta(dsf: DataStoreFile)
      : Option[(DataStoreFile, DataSetMetaTypes.DataSetMetaType)] = {
      DataSetMetaTypes.toDataSetType(dsf.fileTypeId).map(f => (dsf, f))
    }

    def runAllReports(
        path: String,
        dst: DataSetMetaTypes.DataSetMetaType): Seq[DataStoreFile] =
      DataSetReports.runAllIgnoreErrors(Paths.get(path),
                                        dst,
                                        rootReportDir,
                                        opts.jobTypeId,
                                        resultsWriter)

    dataStoreFiles
      .flatMap(toFileMeta)
      .flatten(xs => runAllReports(xs._1.path, xs._2))

  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()

    def runExportAndImport(stdOutLog: DataStoreFile): PacBioDataStore = {

      val datastoreFromZip =
        unzipTo(opts.path, resources.path.resolve("zip-output"))
      val datastoreFiles = loadDataStoreFilesFromDataStore(
        datastoreFromZip.toFile)
      resultsWriter.writeLine(
        s"Loaded ${datastoreFiles.length} from datastore.json")
      val reportDatastoreFiles =
        runReports(datastoreFiles, resources.path, resultsWriter)
      val allDataStoreFiles = Seq(stdOutLog) ++ datastoreFiles ++ reportDatastoreFiles
      val datastore = PacBioDataStore.fromFiles(allDataStoreFiles)
      writeDataStore(
        datastore,
        resources.path.resolve(JobConstants.OUTPUT_DATASTORE_JSON))
      datastore
    }

    val tx = for {
      stdOutLog <- getStdOutLogT(resources, dao)
      datastore <- Try { runExportAndImport(stdOutLog) }
    } yield datastore

    convertTry[PacBioDataStore](tx, resultsWriter, startedAt, resources.jobId)
  }

}
