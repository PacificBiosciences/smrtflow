package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MergeDataSetOptions
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{
  EngineJobEntryPointRecord,
  ServiceDataSetMetadata
}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.validators.ValidateServiceDataSetUtils

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.{Try, Failure, Success}

/**
  * Created by mkocher on 8/17/17.
  */
case class MergeDataSetJobOptions(
    datasetType: DataSetMetaTypes.DataSetMetaType,
    ids: Seq[IdAble],
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  import CommonModelImplicits._

  override def jobTypeId = JobTypeIds.MERGE_DATASETS
  override def toJob() = new MergeDataSetJob(this)

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    Try { resolveEntryPoints(dao) } match {
      case Success(_) => None
      case Failure(ex) =>
        Some(InvalidJobOptionError(s"Invalid options. ${ex.getMessage}"))
    }
  }

  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    val fx = for {
      datasets <- ValidateServiceDataSetUtils.resolveInputs(datasetType,
                                                            ids,
                                                            dao)
      entryPoints <- Future.successful(datasets.map(ds =>
        EngineJobEntryPointRecord(ds.uuid, datasetType.toString)))
    } yield entryPoints

    Await.result(fx, 10.seconds)
  }

}

class MergeDataSetJob(opts: MergeDataSetJobOptions)
    extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore

  import CommonModelImplicits._

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    // FIXME. this needs to be centralized
    val timeout = 10.seconds

    val name = opts.name.getOrElse("Merge-DataSet")

    val fx: Future[Seq[String]] = for {
      datasets <- ValidateServiceDataSetUtils.resolveInputs(opts.datasetType,
                                                            opts.ids,
                                                            dao)
      paths <- Future.successful(datasets.map(_.path))
    } yield paths

    val paths: Seq[String] = Await.result(fx, timeout)

    val oldOpts = MergeDataSetOptions(opts.datasetType.toString,
                                      paths,
                                      name,
                                      opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
