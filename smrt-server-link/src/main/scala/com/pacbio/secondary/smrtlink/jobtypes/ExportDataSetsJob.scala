package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}

import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ExportDataSetsOptions
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{
  BoundServiceEntryPoint,
  DataSetExportServiceOptions,
  EngineJobEntryPointRecord
}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.validators.ValidateServiceDataSetUtils

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

trait ValidateJobUtils {

  // I think this should be deleted.
  def projectJoiner(projectIds: Seq[Int]): Int = {
    val ids = projectIds.toSet
    if (ids.size == 1) ids.head
    else JobConstants.GENERAL_PROJECT_ID
  }

  def validateOutputDir(dir: Path): Future[Path] = {
    if (!dir.toFile.exists)
      Future.failed(
        new UnprocessableEntityError(
          s"The directory ${dir.toString} does not exist"))
    else if (!Files.isWritable(dir))
      Future.failed(new UnprocessableEntityError(
        s"SMRTLink does not have write permissions for the directory ${dir.toString}"))
    else Future.successful(dir)
  }

  def validateOutputPath(p: Path): Future[Path] = {
    val dir = p.getParent
    if (p.toFile.exists)
      Future.failed(
        new UnprocessableEntityError(s"The file $p already exists"))
    else validateOutputDir(dir).map(d => p)
  }
}

/**
  * Created by mkocher on 8/17/17.
  */
case class ExportDataSetsJobOptions(
    datasetType: DataSetMetaTypes.DataSetMetaType,
    ids: Seq[IdAble],
    outputPath: Path,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions
    with ValidateJobUtils {

  import CommonModelImplicits._

  // Need to think about how this is set from the EngineJob or if it's even necessary
  override def jobTypeId = JobTypeIds.EXPORT_DATASETS
  override def toJob() = new ExportDataSetJob(this)

  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    val fx = for {
      datasets <- ValidateServiceDataSetUtils.resolveInputs(datasetType,
                                                            ids,
                                                            dao)
      entryPoints <- Future.successful(datasets.map(ds =>
        EngineJobEntryPointRecord(ds.uuid, datasetType.toString)))
    } yield entryPoints

    Await.result(fx, DEFAULT_TIMEOUT)
  }

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    // This should probably reuse resolveEntryPoints
    val f: Future[Option[InvalidJobOptionError]] = for {
      _ <- validateOutputPath(outputPath)
      _ <- ValidateServiceDataSetUtils.resolveInputs(datasetType, ids, dao)
    } yield None

    val f2 = f.recover {
      case NonFatal(ex) =>
        Some(
          InvalidJobOptionError(
            s"Invalid ExportDataSet options ${ex.getMessage}"))
    }

    Await.result(f2, DEFAULT_TIMEOUT)
  }

}

class ExportDataSetJob(opts: ExportDataSetsJobOptions)
    extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore

  import com.pacbio.common.models.CommonModelImplicits._

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val fx = for {
      datasets <- ValidateServiceDataSetUtils.resolveInputs(opts.datasetType,
                                                            opts.ids,
                                                            dao)
      paths <- Future.successful(datasets.map(p => Paths.get(p.path)))
    } yield paths

    val paths: Seq[Path] = Await.result(fx, opts.DEFAULT_TIMEOUT)

    val oldOpts = ExportDataSetsOptions(opts.datasetType,
                                        paths,
                                        opts.outputPath,
                                        opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
