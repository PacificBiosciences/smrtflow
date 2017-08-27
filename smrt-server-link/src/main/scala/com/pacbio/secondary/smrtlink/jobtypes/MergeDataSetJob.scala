package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultWriter
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MergeDataSetOptions
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{EngineJobEntryPointRecord, ServiceDataSetMetadata}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.validators.ValidateImportDataSetUtils

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 8/17/17.
  */
case class MergeDataSetJobOptions(datasetType: DataSetMetaTypes.DataSetMetaType,
                                  ids: Seq[Int],
                                  name: Option[String],
                                  description: Option[String],
                                  projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.MERGE_DATASETS
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new MergeDataSetJob(this)

  def validateDataSetType(datasetType: String): Future[DataSetMetaTypes.DataSetMetaType] = {
    DataSetMetaTypes.toDataSetType(datasetType)
        .map(d => Future.successful(d))
        .getOrElse(Future.failed(throw new UnprocessableEntityError(s"Unsupported dataset type '$datasetType'")))
  }

  def validatePath(serviceDataSet: ServiceDataSetMetadata): Future[ServiceDataSetMetadata] = {
    val p = Paths.get(serviceDataSet.path)
    if (Files.exists(p) && p.toFile.isFile) Future.successful(serviceDataSet)
    else Future.failed(throw new UnprocessableEntityError(s"Unable to find DataSet id:${serviceDataSet.id} UUID:${serviceDataSet.uuid} Path:$p"))
  }

  /**
    * Resolve the DataSet and Validate the Path to the XML file.
    *
    * In a future iteration, this should validate all the external resources in the DataSet XML file
    *
    * @param dsType DataSet MetaType
    * @param dsId DataSet Int
    * @return
    */
  def resolveAndValidatePath(dsType: DataSetMetaTypes.DataSetMetaType, dsId: Int, dao: JobsDao): Future[ServiceDataSetMetadata] = {

    import CommonModelImplicits._

    for {
      dsm <- ValidateImportDataSetUtils.resolveDataSet(dsType.toString, dsId, dao)
      validatedDataSet <- validatePath(dsm)
    } yield validatedDataSet

  }

  def validateDataSetsExist(datasets: Seq[Int],
                            dsType: DataSetMetaTypes.DataSetMetaType,
                            dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] = {
    Future.sequence(datasets.map(id => resolveAndValidatePath(dsType, id, dao)))
  }

  def resolveInputs(dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] =
    Future.sequence(ids.map(x=> resolveAndValidatePath(datasetType, x, dao)))

  override def resolveEntryPoints(dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    val fx = for {
      datasets <- resolveInputs(dao)
      entryPoints <- Future.successful(datasets.map(ds => EngineJobEntryPointRecord(ds.uuid, datasetType.toString)))
    } yield entryPoints

    Await.result(fx, 5.seconds)
  }

}

class MergeDataSetJob(opts: MergeDataSetJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore

  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    // FIXME. this needs to be centralized
    val timeout = 10.seconds

    val name = opts.name.getOrElse("Merge-DataSet")

    val fx: Future[Seq[String]] = for {
      datasets <- opts.resolveInputs(dao)
      paths <- Future.successful(datasets.map(_.path))
    } yield paths

    val paths:Seq[String] = Await.result(fx, timeout)

    val oldOpts = MergeDataSetOptions(opts.datasetType.toString, paths, name, opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
