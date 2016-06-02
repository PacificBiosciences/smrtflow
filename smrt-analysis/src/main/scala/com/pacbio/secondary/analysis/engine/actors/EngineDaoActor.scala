package com.pacbio.secondary.analysis.engine.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.pipe
import com.pacbio.secondary.analysis.engine.CommonMessages
import com.pacbio.secondary.analysis.engine.EngineDao.JobEngineDataStore
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels.{DataStoreFile, DataStoreJobFile, PacBioDataStore, RunnableJob}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * Actor to talk to the persistence layer.
 *
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
      dao.getJobs(1000).map { rs =>
        val states = rs.map(_.state).toSet
        // Summary of job states by type
        val results = states.toList.map(x => (x, rs.count(e => e.state == x)))
        log.debug(s"Results $results")
        dao.getDataStoreFiles.map { dsJobFiles =>
          log.debug(s"Number of DataStore files ${dsJobFiles.size}")
          dsJobFiles.foreach { x => log.debug(s"DS File $x") }
          rs.foreach { x => log.debug(s"Job result id ${x.id} -> ${x.uuid} ${x.state} ${x.jobTypeId}") }
        }
      }

    case GetJobStatusByUUID(uuid) => dao.getJobByUUID(uuid) pipeTo sender

    case AddNewJob(job) => dao.addRunnableJob(RunnableJob(job, AnalysisJobStates.CREATED))
      .map(_ => SuccessMessage(s"Successfully added Job ${job.uuid}")) pipeTo sender

    case HasNextRunnableJobWithId => dao.getNextRunnableJobWithId pipeTo sender

    case UpdateJobStatus(uuid, state) =>
      dao.updateJobStateByUUID(uuid, state).map { _ =>
        listeners.foreach(x => x ! UpdateJobStatus(uuid, state))
        SuccessMessage(s"Successfully updated state of job ${uuid.toString} to ${state.toString}")
      } pipeTo sender

    case ImportDataStoreFile(dataStoreFile, jobUUID) =>
      log.debug(s"importing datastore file $dataStoreFile for job ${jobUUID.toString}")
      dao.addDataStoreFile(DataStoreJobFile(jobUUID, dataStoreFile)) pipeTo sender

    case PacBioImportDataSet(x, jobUUID) =>
      x match {
        case ds: DataStoreFile =>
          log.info(s"importing dataset from $ds")
          dao.addDataStoreFile(DataStoreJobFile(jobUUID, ds)) pipeTo sender

        case ds: PacBioDataStore =>
          log.info(s"loading files from datastore $ds")
          val results = Future.sequence(ds.files.map { f => dao.addDataStoreFile(DataStoreJobFile(jobUUID, f)) })
          val failures = results.map(_.map(x => x.left.toOption).filter(_.isDefined))
          failures.map { f =>
            if (f.nonEmpty) {
              Left(FailedMessage(s"Failed to import datastore $failures"))
            } else {
              Right(SuccessMessage(s"Successfully imported files from $x"))
            }
          } pipeTo sender
      }

    case UpdateJobOutputDir(uuid, path) =>
      // TODO(smcclellan): Why is this commented out? Can this message be removed?
      //dao.updateJobOutputDir(uuid, path)
      sender ! Right(SuccessMessage(s"Successfully updated jobOptions directory ${uuid.toString} dir ${path.toAbsolutePath}"))

    case x =>
      log.info(s"Unhandled engine DAO message. '$x'")
  }
}
