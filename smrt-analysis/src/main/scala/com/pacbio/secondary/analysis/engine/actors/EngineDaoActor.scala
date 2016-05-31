package com.pacbio.secondary.analysis.engine.actors

import java.nio.file.Paths
import java.util.UUID

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.engine.EngineDao.JobEngineDataStore
import com.pacbio.secondary.analysis.engine.CommonMessages
import com.pacbio.secondary.analysis.jobs.JobModels.{DataStoreJobFile, RunnableJob, PacBioDataStore, DataStoreFile}
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates

import scala.collection.mutable

/**
 * Actor to talk to the persistence layer.
 *
 * @param dao
 * @param listeners Actors that will be notified when jobs are complete or updated state
 */
class EngineDaoActor(dao: JobEngineDataStore, listeners: Seq[ActorRef]) extends Actor with ActorLogging {

  import CommonMessages._


  override def preStart(): Unit = {
    log.info("Starting DAO actor")
  }

  def receive: Receive = {

    // FIXME Need to remove all this boiler plate
    case GetAllJobs(limit) =>
      sender ! dao.getJobs(limit)

    case GetSystemJobSummary =>
      val rs = dao.getJobs(1000)
      val states = rs.map(_.state).toSet
      // Summary of job states by type
      val results = states.toList.map(x => (x, rs.count(e => e.state == x)))
      log.debug(s"Results $results")
      val dsJobFiles = dao.getDataStoreFiles
      log.debug(s"Number of DataStore files ${dsJobFiles.size}")
      dsJobFiles.foreach { x => log.debug(s"DS File $x")}
      rs.foreach { x => log.debug(s"Job result id ${x.id} -> ${x.uuid} ${x.state} ${x.jobTypeId}") }

    case GetJobStatusByUUID(uuid) =>
      sender ! dao.getJobByUUID(uuid)

    case AddNewJob(job) =>
      dao.addRunnableJob(RunnableJob(job, AnalysisJobStates.CREATED))
      sender ! SuccessMessage(s"Successfully added Job ${job.uuid}")

    case HasNextRunnableJobWithId =>
      sender ! dao.getNextRunnableJobWithId

    case UpdateJobStatus(uuid, state) =>
      dao.updateJobStateByUUID(uuid, state)
      listeners.foreach(x => x ! UpdateJobStatus(uuid, state))
      sender ! SuccessMessage(s"Successfully updated state of job ${uuid.toString} to ${state.toString}")

    case ImportDataStoreFile(dataStoreFile, jobUUID) =>
      log.debug(s"importing datastore file $dataStoreFile for job ${jobUUID.toString}")
      sender ! dao.addDataStoreFile(DataStoreJobFile(jobUUID, dataStoreFile))

    case PacBioImportDataSet(x, jobUUID) =>
      x match {
        case ds: DataStoreFile =>
          log.info(s"importing dataset from $ds")
          dao.addDataStoreFile(DataStoreJobFile(jobUUID, ds))
          sender ! Right(SuccessMessage(s"Successfully imported files from $x"))

        case ds: PacBioDataStore =>
          log.info(s"loading files from datastore $ds")
          val results = ds.files.map { dsFile => dao.addDataStoreFile(DataStoreJobFile(jobUUID, dsFile)) }
          val failures = results.map(x => x.left.toOption).flatMap(a => a)
          if (failures.nonEmpty) {
            Left(FailedMessage(s"Failed to import datastore $failures"))
          } else {
            sender ! Right(SuccessMessage(s"Successfully imported files from $x"))
          }
      }

    case UpdateJobOutputDir(uuid, path) =>
      //dao.updateJobOutputDir(uuid, path)
      sender ! Right(SuccessMessage(s"Successfully updated jobOptions directory ${uuid.toString} dir ${path.toAbsolutePath}"))

    case x =>
      log.info(s"Unhandled engine DAO message. '$x'")
  }
}
