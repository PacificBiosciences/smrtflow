package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}

import com.pacbio.common.models.CommonModels._
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{InvalidJobOptionError, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.DeleteDatasetsOptions
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{EngineJobEntryPointRecord, ServiceDataSetMetadata}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.validators.ValidateServiceDataSetUtils

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 8/17/17.
  */
case class DeleteDataSetJobOptions(ids: Seq[IdAble],
                                   datasetType: DataSetMetaTypes.DataSetMetaType,
                                   removeFiles: Boolean = true,
                                   name: Option[String],
                                   description: Option[String],
                                   projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {

  import com.pacbio.common.models.CommonModelImplicits._

  override def jobTypeId = JobTypeIds.DELETE_DATASETS
  override def toJob() = new DeleteDataSetJob(this)

  private def validateSubreadSetType(datasetType: DataSetMetaTypes.DataSetMetaType): Future[DataSetMetaTypes.DataSetMetaType] = {
    datasetType match {
      case DataSetMetaTypes.Subread => Future.successful(datasetType)
      case x =>  Future.failed(new UnprocessableEntityError(s"DataSetMetaType $x is not support. Only dataset type ${DataSetMetaTypes.Subread} is supported."))
    }
  }

  /**
    * Get a list of ALL datasets to be deleted from the system.
    */
  def getAllDataSets(dao: JobsDao):Future[Seq[ServiceDataSetMetadata]] = {
    for {
      datasets <- getDataSets(dao)
      upstreamDataSets <- getUpstreamDataSets(datasets.map(_.jobId), DataSetMetaTypes.Subread.dsId, dao)
      allDataSets <- Future.successful(datasets ++ upstreamDataSets)
    } yield allDataSets
  }

  def getAllDataSetsAndDelete(dao: JobsDao): Future[(String, Seq[ServiceDataSetMetadata])] = {
    for {
      datasets <- getAllDataSets(dao)
      msgs <- Future.sequence(datasets.map(d => dao.deleteDataSetById(d.uuid)))
    } yield (msgs.map(_.message).reduceLeftOption(_ + "\n" + _).getOrElse("No datasets found to delete."), datasets)
  }

  private def getUpstreamDataSets(jobIds: Seq[Int], dsMetaType: String, dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] = {
    for {
      jobs <- Future.sequence{ jobIds.map(j => dao.getJobById(j)) }
      mergeJobs <- Future.successful(jobs.filter(_.jobTypeId == JobTypeIds.MERGE_DATASETS.id))
      entryPoints <- Future.sequence {  mergeJobs.map(j => dao.getJobEntryPoints(j.id)) }.map(_.flatten)
      datasets <- Future.sequence { entryPoints.map(ep => ValidateServiceDataSetUtils.resolveDataSet(DataSetMetaTypes.fromString(ep.datasetType).get, ep.datasetUUID, dao)) }
    } yield datasets
  }

  /**
    * Get the original entry points used for each job provided.
    *
    */
  def getDataSets(dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] = {
    for {
      _ <- validateSubreadSetType(datasetType)
      datasets <- Future.sequence(ids.map(x => ValidateServiceDataSetUtils.resolveDataSet(DataSetMetaTypes.Subread, x, dao)))
    } yield datasets
  }

  override def resolveEntryPoints(dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    Await.result(getDataSets(dao).map(_.map(x => EngineJobEntryPointRecord(x.uuid, datasetType.toString))), DEFAULT_TIMEOUT)
  }


  override def validate(dao: JobsDao, config: SystemJobConfig):Option[InvalidJobOptionError] = {
    Try(Await.result(getDataSets(dao), DEFAULT_TIMEOUT)) match {
      case Success(_) => None
      case Failure(ex) => Some(InvalidJobOptionError(s"Invalid options. ${ex.getMessage}"))
    }
  }


}

class DeleteDataSetJob(opts: DeleteDataSetJobOptions) extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore

  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    // THIS needs to be fixed. It should delete from the file system and update the db as a computation single unit
    // to make sure there's a
    // This requires de-tangling the old-style job from the new model.
    val results = Await.result(opts.getAllDataSetsAndDelete(dao), opts.DEFAULT_TIMEOUT)
    val paths:Seq[Path] = results._2.map(p => Paths.get(p.path))

    val oldOpts = DeleteDatasetsOptions(paths, opts.removeFiles, opts.getProjectId())
    oldOpts.toJob.run(resources, resultsWriter)
  }
}
